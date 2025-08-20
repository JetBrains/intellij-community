package com.intellij.grazie.utils;

import ai.grazie.nlp.langs.Language;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.detection.LangDetector;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextProblem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringOperation;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.grazie.text.TextExtractor.findAllTextContents;


public final class HighlightingUtil {

  private static final Pattern COULD_BE_ENGLISH = Pattern.compile("([a-zA-Z]|[^\\p{L}])*+");
  public static final Comparator<TextContent> BY_TEXT_START = Comparator.comparing(tc -> tc.textOffsetToFile(0));

  //todo use a more decent API when it appears (https://youtrack.jetbrains.com/issue/IDEA-294972)
  public static boolean skipExpensivePrecommitAnalysis(PsiFile file) {
    for (PsiFile root : file.getViewProvider().getAllFiles()) {
      var function = InspectionProfileWrapper.getCustomInspectionProfileWrapper(root);
      if (function != null &&
          function.getClass().getName().contains("com.intellij.codeInsight.daemon.impl.MainPassesRunner") &&
          Registry.is("grazie.skip.precommit.checks")) {
        return true;
      }
    }
    return false;
  }

  public static boolean shouldEnableHighlighting(PsiFile file) {
    return shouldEnableHighlighting(file.getProject(), file.getViewProvider().getVirtualFile());
  }

  public static boolean shouldEnableHighlighting(Project project, VirtualFile vFile) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    return !fileIndex.isExcluded(vFile) && !fileIndex.isInLibrary(vFile);
  }

  public static Set<TextContent.TextDomain> checkedDomains() {
    return GrazieInspection.Companion.checkedDomains();
  }

  public static List<TextRange> toFileRanges(TextContent text, TextRange rangeInText) {
    return ContainerUtil.filter(text.intersection(text.textRangeToFile(rangeInText)), r -> !r.isEmpty());
  }

  public static List<TextRange> getFileHighlightRanges(TextProblem problem) {
    return ContainerUtil.flatMap(problem.getHighlightRanges(), r -> toFileRanges(problem.getText(), r));
  }

  public static TextRange selectionRange(Editor editor) {
    return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
  }

  public static <T> Map<PsiElement, List<Pair<TextRange, T>>> analyzeForAnnotator(
    TextContent text, Key<Pair<Long, Map<PsiElement, List<Pair<TextRange, T>>>>> key, Supplier<List<Pair<TextRange, T>>> supplier
  ) {
    long modCount = grazieConfigTracker().getModificationCount();
    var cached = text.getUserData(key);
    if (cached == null || !cached.first.equals(modCount)) {
      text.putUserData(key, cached = Pair.create(modCount, distributeByMinimalLeaves(text, supplier.get())));
    }
    return cached.second;
  }

  private static <T> Map<PsiElement, List<Pair<TextRange, T>>> distributeByMinimalLeaves(
    TextContent text, List<Pair<TextRange, T>> data
  ) {
    Map<PsiElement, List<Pair<TextRange, T>>> result = new HashMap<>();
    for (Pair<TextRange, T> pair : data) {
      TextRange range = pair.first;
      PsiElement minimal = SyntaxTraverser.psiApi()
        .parents(text.getContainingFile().findElementAt(range.getStartOffset()))
        .find(e -> e.getTextRange().getEndOffset() >= range.getEndOffset());
      result.computeIfAbsent(minimal, __ -> new ArrayList<>()).add(pair);
    }
    return result;
  }

  public static ModificationTracker grazieConfigTracker() {
    return ApplicationManager.getApplication().getService(GrazieConfig.class);
  }

  public static boolean isTooLargeText(List<TextContent> texts) {
    return texts.stream().mapToInt(t -> t.length()).sum() > 50_000;
  }

  public static void applyTextChanges(Document document, List<StringOperation> changes) {
    for (StringOperation r : StreamEx.of(changes).sortedBy(c -> -c.getRange().getStartOffset())) {
      document.replaceString(r.getRange().getStartOffset(), r.getRange().getEndOffset(), r.getReplacement());
    }
  }

  public static @Nullable Lang activeEnglish() {
    return findInstalledLang(Language.ENGLISH);
  }

  public static @Nullable Lang findInstalledLang(@NotNull Language language) {
    return StreamEx.of(GrazieConfig.Companion.get().getAvailableLanguages())
      .findFirst(lang -> lang.getIso() == language.getIso())
      .orElse(null);
  }

  @Nullable
  public static Language detectPreferringEnglish(String text) {
    var lang = LangDetector.INSTANCE.getLanguage(text);
    if (lang != null && findInstalledLang(lang) == null) lang = null;
    return lang == null && COULD_BE_ENGLISH.matcher(text).matches() ? Language.ENGLISH : lang;
  }

  private static final Pattern trackerIssuePrefix = Pattern.compile("\\s*([A-Z]\\w+-\\d+):?\\s+.*");

  public static int stripPrefix(TextContent content) {
    int start = 0;
    String text = content.toString();

    if (CommitMessage.isCommitMessage(content.getContainingFile())) {
      if (text.startsWith("[")) {
        int rBrace = text.indexOf(']');
        if (rBrace >= 1 && rBrace <= 30) {
          start = rBrace + 1;
        }
      } else {
        int colon = text.indexOf(':');
        if (colon >= 1 && colon <= 30 && text.substring(0, colon).chars().filter(Character::isWhitespace).count() <= 1) {
          start = colon + 1;
        }
      }

      var issueMatch = trackerIssuePrefix.matcher(text.substring(start));

      if (issueMatch.matches()) {
        start += issueMatch.group(1).length() + 1;
      }
    }

    while (start < content.length() && Character.isWhitespace(content.charAt(start))) {
      start++;
    }

    return start;
  }

  public static List<TextContent> getCheckedFileTexts(FileViewProvider vp) {
    return CachedValuesManager.getManager(vp.getManager().getProject()).getCachedValue(vp, () -> {
      List<TextContent> contents = ContainerUtil.sorted(findAllTextContents(vp, checkedDomains()), BY_TEXT_START);
      return CachedValueProvider.Result.create(contents, vp.getAllFiles().get(0), grazieConfigTracker());
    });
  }

  public static TextRange toIdeaRange(ai.grazie.rules.tree.TextRange reported) {
    return new TextRange(reported.start(), reported.end());
  }
}
