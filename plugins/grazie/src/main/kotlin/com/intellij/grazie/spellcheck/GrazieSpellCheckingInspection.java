// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck;

import ai.grazie.nlp.langs.LanguageISO;
import ai.grazie.spell.suggestion.ranker.AsciiRanker;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.SuppressibleSpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.Consumer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.getSpellcheckingStrategy;

public final class GrazieSpellCheckingInspection extends SpellCheckingInspection {

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    if (element != null) {
      SpellcheckingStrategy strategy = getSpellcheckingStrategy(element);
      if (strategy instanceof SuppressibleSpellcheckingStrategy) {
        return ((SuppressibleSpellcheckingStrategy)strategy).getSuppressActions(element, getShortName());
      }
    }
    return super.getBatchSuppressActions(element);
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    SpellcheckingStrategy strategy = getSpellcheckingStrategy(element);
    if (strategy instanceof SuppressibleSpellcheckingStrategy) {
      return ((SuppressibleSpellcheckingStrategy)strategy).isSuppressedFor(element, getShortName());
    }
    return super.isSuppressedFor(element);
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return SPELL_CHECKING_INSPECTION_TOOL_NAME;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return super.buildVisitor(holder, isOnTheFly);
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return Objects.requireNonNull(HighlightDisplayLevel.find(SpellCheckerSeveritiesProvider.TYPO));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    if (!Registry.is("spellchecker.inspection.enabled", true) ||
        InspectionProfileManager.hasTooLowSeverity(session, this) ||
        InjectedLanguageManager.getInstance(holder.getProject()).isFrankensteinInjection(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    var scopes = buildAllowedScopes();
    SpellCheckerManager manager = SpellCheckerManager.getInstance(holder.getProject());
    return new PsiElementVisitor() {
      @Override
      public void visitWhiteSpace(@NotNull PsiWhiteSpace space) { }

      @Override
      public void visitElement(final @NotNull PsiElement element) {
        if (holder.getResultCount() > 1000 || element.getNode() == null) return;

        var strategy = getSpellcheckingStrategy(element);
        if (strategy == null || !strategy.elementFitsScope(element, scopes) || isCopyrightComment(strategy, element)) return;

        SpellCheckingResult result = GrazieTextLevelSpellCheckingExtension.INSTANCE.spellcheck(
          element, strategy, session,
          typo -> {
            if (hasSameNamedReferenceInFile(typo.getWord(), element, strategy)) return;
            registerProblem(typo, holder);
          }
        );
        if (result == SpellCheckingResult.Checked) return;

        tokenize(
          strategy, element,
          new MyTokenConsumer(manager, strategy, holder, LanguageNamesValidation.INSTANCE.forLanguage(element.getLanguage())),
          scopes
        );
      }
    };
  }

  private Set<SpellCheckingScope> buildAllowedScopes() {
    var result = new HashSet<SpellCheckingScope>();
    if (processLiterals) {
      result.add(SpellCheckingScope.Literals);
    }
    if (processComments) {
      result.add(SpellCheckingScope.Comments);
    }
    if (processCode) {
      result.add(SpellCheckingScope.Code);
    }
    return result;
  }

  private static void addBatchDescriptor(@NotNull PsiElement element,
                                         @NotNull TextRange textRange,
                                         @NotNull String word,
                                         @NotNull ProblemsHolder holder) {
    var fixes = SpellcheckingStrategy.getDefaultBatchFixes(element, textRange, word);
    ProblemDescriptor problemDescriptor = createProblemDescriptor(element, textRange, fixes, false);
    holder.registerProblem(problemDescriptor);
  }

  private static void addRegularDescriptor(@NotNull PsiElement element, @NotNull TextRange textRange, @NotNull ProblemsHolder holder,
                                           boolean useRename, String wordWithTypo, Set<String> suggestions) {
    SpellcheckingStrategy strategy = getSpellcheckingStrategy(element);

    LocalQuickFix[] fixes = strategy != null
                            ? strategy.getRegularFixes(element, textRange, useRename, wordWithTypo, suggestions)
                            : SpellcheckingStrategy.getDefaultRegularFixes(useRename, wordWithTypo, element, textRange, suggestions);

    ProblemDescriptor problemDescriptor = createProblemDescriptor(element, textRange, fixes, true);
    holder.registerProblem(problemDescriptor);
  }

  private static ProblemDescriptor createProblemDescriptor(PsiElement element, TextRange textRange,
                                                           LocalQuickFix[] fixes,
                                                           boolean onTheFly) {
    final String description = SpellCheckerBundle.message("typo.in.word.ref");
    return new ProblemDescriptorBase(element, element, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     false, textRange, onTheFly, onTheFly);
  }

  @SuppressWarnings("PublicField")
  public boolean processCode = true;
  public boolean processLiterals = true;
  public boolean processComments = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("processCode", SpellCheckerBundle.message("process.code")),
      checkbox("processLiterals", SpellCheckerBundle.message("process.literals")),
      checkbox("processComments", SpellCheckerBundle.message("process.comments"))
    );
  }

  private static final class MyTokenConsumer extends TokenConsumer implements Consumer<TextRange> {
    private static final Pattern NON_ENGLISH_LETTERS = Pattern.compile(".*[^a-zA-Z].*");

    private final Set<String> myAlreadyChecked = CollectionFactory.createSmallMemoryFootprintSet();
    private final SpellCheckerManager myManager;
    private final ProblemsHolder myHolder;
    private final NamesValidator myNamesValidator;
    private final SpellcheckingStrategy myStrategy;
    private boolean myCodeLike;
    private PsiElement myElement;
    private String myText;
    private boolean myUseRename;
    private int myOffset;

    MyTokenConsumer(SpellCheckerManager manager, SpellcheckingStrategy strategy, ProblemsHolder holder, NamesValidator namesValidator) {
      myManager = manager;
      myStrategy = strategy;
      myHolder = holder;
      myNamesValidator = namesValidator;
    }

    @Override
    public void consumeToken(final PsiElement element,
                             final String text,
                             final boolean useRename,
                             final int offset,
                             TextRange rangeToCheck,
                             Splitter splitter) {
      myElement = element;
      myText = text;
      myUseRename = useRename;
      myOffset = offset;
      myCodeLike = myStrategy.elementFitsScope(myElement, Set.of(SpellCheckingScope.Code));
      splitter.split(text, rangeToCheck, this);
    }

    @Override
    public void consume(TextRange range) {
      // Tokenization of large texts can produce a lot of tokens, but we are inside RA
      ProgressManager.checkCanceled();
      String word = range.substring(myText);
      if (!myHolder.isOnTheFly() && myAlreadyChecked.contains(word)) {
        return;
      }

      boolean keyword = myNamesValidator.isKeyword(word, myElement.getProject());
      if (keyword || !hasProblem(word, range) || hasSameNamedReferenceInFile(word, myElement, myStrategy)) {
        return;
      }

      range = myStrategy.getTokenizer(myElement)
        .getHighlightingRange(myElement, myOffset, range);
      assert range.getStartOffset() >= 0;

      if (!myHolder.isOnTheFly()) {
        myAlreadyChecked.add(word);
      }
      registerProblem(myHolder, myElement, range, myUseRename, word);
    }

    private boolean hasCamelCaseMatch(String word, TextRange range) {
      Set<String> camelCaseWords = myManager.getUserCamelCaseWords()
        .stream()
        .filter(camelCaseWord -> camelCaseWord.contains(word))
        .collect(Collectors.toSet());
      if (camelCaseWords.isEmpty()) {
        return false;
      }

      String text = myElement.getText();
      for (String camelCaseWord : camelCaseWords) {
        ProgressManager.checkCanceled();
        int[] indexes = new StringSearcher(camelCaseWord, false, true).findAllOccurrences(text);
        for (int index : indexes) {
          TextRange hitRange = new TextRange(index, index + camelCaseWord.length());
          if (range.intersectsStrict(hitRange)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean hasProblem(String word, TextRange range) {
      if (!myManager.hasProblem(word)) {
        return false;
      }

      // Check if a user has added CamelCase word to the project / app dictionary
      if (hasCamelCaseMatch(word, range)) {
        return false;
      }

      if (isOnlyEnglishDictionaryEnabled(myElement.getProject())) {
        return true;
      }

      // If the word isn't "code" or contains letters outside the English alphabet,
      // then diacritic check should be skipped
      if (!myCodeLike || NON_ENGLISH_LETTERS.matcher(word).matches()) {
        return true;
      }

      Project project = myElement.getProject();
      return SpellCheckerManager.getInstance(project).getSuggestions(word)
        .stream()
        .filter(suggestion -> RenameUtil.isValidName(project, myElement, suggestion))
        .noneMatch(suggestion -> AsciiRanker.equalsIgnoringDiacritics(word, suggestion));
    }

    private static boolean isOnlyEnglishDictionaryEnabled(Project project) {
      Set<LanguageISO> languages = GrazieConfig.Companion.get().getEnabledLanguages()
        .stream()
        .map(it -> it.getIso())
        .collect(Collectors.toSet());
      if (languages.size() != 1 || !languages.contains(LanguageISO.EN)) {
        return false;
      }

      List<String> paths = SpellCheckerSettings.getInstance(project).getCustomDictionariesPaths();
      if (paths != null && !paths.isEmpty()) {
        GrazieSpellCheckerEngine engine = GrazieSpellCheckerEngine.getInstance(project);
        return !ContainerUtil.exists(paths, dictionaryName -> engine.isDictionaryLoad(dictionaryName));
      }
      return true;
    }
  }

  private static boolean hasSameNamedReferenceInFile(String word, PsiElement element, SpellcheckingStrategy strategy) {
    if (!strategy.elementFitsScope(element, Set.of(SpellCheckingScope.Comments))) {
      return false;
    }

    PsiFile file = element.getContainingFile();
    Map<String, Boolean> references = CachedValuesManager.getProjectPsiDependentCache(file, (psi) -> new ConcurrentHashMap<>());
    return references.computeIfAbsent(word, key -> hasSameNamedReferencesInFile(key, file));
  }

  private static boolean hasSameNamedReferencesInFile(String word, PsiFile file) {
    int[] occurrences = new StringSearcher(word, true, true).findAllOccurrences(file.getText());
    if (occurrences.length <= 1) {
      return false;
    }

    if (DumbService.isDumb(file.getProject())) {
      for (int occurrence : occurrences) {
        PsiElement element = file.findElementAt(occurrence);
        if (element != null) {
          SpellcheckingStrategy strategy = getSpellcheckingStrategy(element);
          if (strategy != null && !strategy.elementFitsScope(element, Set.of(SpellCheckingScope.Comments))) {
            return true;
          }
        }
      }
      return false;
    }

    for (int occurrence : occurrences) {
      PsiReference reference = file.findReferenceAt(occurrence);
      PsiElement resolvedReference = reference != null ? reference.resolve() : null;
      if (reference != null && resolvedReference != null && reference.getElement() != resolvedReference) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCopyrightComment(SpellcheckingStrategy strategy, PsiElement psi) {
    return strategy.elementFitsScope(psi, Set.of(SpellCheckingScope.Comments))
           && StringUtil.containsIgnoreCase(psi.getText(), "Copyright")
           && isAtFileStart(psi);
  }

  private static boolean isAtFileStart(PsiElement psi) {
    PsiFile file = psi.getContainingFile();
    int textStart = psi.getTextRange().getStartOffset();
    return file.getViewProvider().getContents().subSequence(0, textStart).chars().noneMatch(Character::isLetterOrDigit);
  }

  private static void registerProblem(@NotNull SpellingTypo typo, @NotNull ProblemsHolder holder) {
    registerProblem(holder, typo.getElement(), typo.getRange(), false, typo.getWord(), typo.getFixes());
  }

  private static void registerProblem(@NotNull ProblemsHolder holder,
                                      @NotNull PsiElement element,
                                      @NotNull TextRange range,
                                      boolean useRename,
                                      String word) {
    registerProblem(holder, element, range, useRename, word, null);
  }

  private static void registerProblem(@NotNull ProblemsHolder holder,
                                      @NotNull PsiElement element,
                                      @NotNull TextRange range,
                                      boolean useRename,
                                      String word,
                                      Set<String> suggestions) {
    if (holder.isOnTheFly()) {
      addRegularDescriptor(element, range, holder, useRename, word, suggestions);
    }
    else {
      addBatchDescriptor(element, range, word, holder);
    }
  }
}
