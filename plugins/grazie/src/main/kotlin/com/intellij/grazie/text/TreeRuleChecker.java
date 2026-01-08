package com.intellij.grazie.text;

import ai.grazie.gec.model.problem.ActionSuggestion;
import ai.grazie.gec.model.problem.Problem;
import ai.grazie.gec.model.problem.ProblemFix;
import ai.grazie.gec.model.problem.SuppressableKind;
import ai.grazie.nlp.langs.Language;
import ai.grazie.rules.Example;
import ai.grazie.rules.MatchingResult;
import ai.grazie.rules.NodeRuleMatch;
import ai.grazie.rules.RuleMatch;
import ai.grazie.rules.document.Delimiter;
import ai.grazie.rules.document.DocumentRule;
import ai.grazie.rules.document.DocumentSentence;
import ai.grazie.rules.settings.RuleSetting;
import ai.grazie.rules.settings.Setting;
import ai.grazie.rules.settings.TextStyle;
import ai.grazie.rules.toolkit.LanguageToolkit;
import ai.grazie.rules.tree.Parameter;
import ai.grazie.rules.tree.Tree;
import ai.grazie.rules.tree.Tree.ParameterValues;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.inspection.ai.RephraseAction;
import com.intellij.grazie.ide.inspection.auto.AutoFix;
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable;
import com.intellij.grazie.rule.ParsedSentence;
import com.intellij.grazie.rule.RuleIdeClient;
import com.intellij.grazie.rule.SentenceBatcher;
import com.intellij.grazie.rule.SentenceTokenizer;
import com.intellij.grazie.style.ConfigureSuggestedParameter;
import com.intellij.grazie.style.TextLevelFix;
import com.intellij.grazie.text.TextContent.TextDomain;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.grazie.utils.Text;
import com.intellij.grazie.utils.TextStyleDomain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringOperation;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.intellij.grazie.text.GrazieProblem.getQuickFixText;
import static com.intellij.grazie.text.GrazieProblem.visualizeSpace;
import static com.intellij.grazie.utils.TextUtilsKt.getTextDomain;
import static com.intellij.grazie.utils.UtilsKt.ijRange;

@SuppressWarnings("NonAsciiCharacters")
public final class TreeRuleChecker {
  private static final Logger LOG = Logger.getInstance(TreeRuleChecker.class);
  public static final String EN_STYLE_CATEGORY = "Style";
  private static final Map<String, String> punctuationCategories = Map.of(
    "en", "Punctuation",
    "ru", "Пунктуация",
    "uk", "Пунктуація",
    "de", "Interpunktion"
  );
  private static final Map<String, String> grammarCategories = Map.of(
    "en", "Grammar",
    "ru", "Грамматика",
    "uk", "Граматика",
    "de", "Grammatik"
  );
  private static final Map<String, String> styleCategories = Map.of(
    "en", EN_STYLE_CATEGORY,
    "ru", "Стиль",
    "uk", "Стиль",
    "de", "Stil"
  );
  private static final Map<String, String> semanticCategories = Map.of(
    "en", "Semantics",
    "ru", "Логические ошибки",
    "uk", "Логічні помилки",
    "de", "Semantische Unstimmigkeiten"
  );
  private static final Map<String, String> typographyCategories = Map.of(
    "en", "Typography",
    "ru", "Типографика",
    "uk", "Типографія",
    "de", "Typografie"
  );
  private static final Map<String, String> spellingCategories = Map.of(
    "en", "Possible Typo",
    "ru", "Проверка орфографии",
    "uk", "Орфографія",
    "de", "Mögliche Tippfehler"
  );

  @ApiStatus.Internal
  public static final String SMART_APOSTROPHE = "Grazie.RuleEngine.En.Typography.SMART_APOSTROPHE";

  public static List<Rule> getRules(Language language) {
    if (!StyleConfigurable.getRuleEngineLanguages().contains(language) || SentenceBatcher.findInstalledLTLanguage(language) == null) {
      return List.of();
    }

    LanguageToolkit toolkit = LanguageToolkit.forLanguage(language);
    return ContainerUtil.map(toolkit.publishedRules(), rule -> toGrazieRule(rule));
  }

  public static Rule toGrazieRule(ai.grazie.rules.Rule rule) {
    String langCode = rule.language().getIso().toString();
    String category =
      rule.id.startsWith("Style.") ? styleCategories.get(langCode) :
      rule.id.startsWith("Typography.") ? typographyCategories.get(langCode) :
      rule.id.startsWith("Punctuation.") ? punctuationCategories.get(langCode) :
      rule.id.startsWith("Semantics.") ? semanticCategories.get(langCode) :
      rule.id.startsWith("Spelling.") ? spellingCategories.get(langCode) :
      grammarCategories.get(langCode);
    String id = rule.globalId();
    List<String> categories = rule.isStyleLike()
                              ? List.of(styleCategories.get(langCode))
                              : List.of(category);
    return new Rule(id, rule.language(), rule.displayName, categories.getFirst()) {

      @Override
      public List<String> getCategories() {
        return categories;
      }

      @Override
      public @NotNull String getDescription() {
        List<Example> examples = rule.getExamples(RuleIdeClient.INSTANCE);
        String result = rule.getDescription(RuleIdeClient.INSTANCE) + "<br><br>";
        if (!examples.isEmpty()) {
          result += "<p style='padding-bottom:5px;'>" +
                    GrazieBundle.message("grazie.settings.grammar.rule.examples") +
                    "</p>" +
                    "<table style='width:100%;' cellspacing=0 cellpadding=0>\n";
          for (Example example : examples) {
            String corrections = StreamEx.of(example.correctedTexts()).map(s -> s + "<br>").joining();
            result += renderExampleRow(example, corrections);
          }

          result += "</table><br/>";
        }

        if (!"en".equals(langCode)) {
          result += GrazieBundle.message("grazie.settings.grammar.cloud.only.rule") + "<br><br>";
        }

        return result; //todo[lene] should it be updated? + GrazieProBundle.msg("grazie.settings.grammar.powered.by.pro");
      }

      @Override
      public boolean isEnabledByDefault(TextStyleDomain domain) {
        return rule.isRuleEnabledByDefault(GrazieConfig.Companion.get().getTextStyle(domain), RuleIdeClient.INSTANCE);
      }

      @Override
      public Setting getFeaturedSetting() {
        return new RuleSetting(rule);
      }

      @Override
      public @Nullable URL getUrl() {
        return rule.url;
      }
    };
  }

  private static String renderExampleRow(Example example, String corrections) {
    String result =
      "<tr><td valign='top' style='padding-bottom: 5px; padding-right: 5px; color: gray;'>" +
      GrazieBundle.message("grazie.settings.grammar.rule.incorrect") + "&nbsp;</td>" +
      "<td style='padding-bottom: 5px; width: 100%'>" + visualizeSpace(example.errorText()) + "</td></tr>";
    if (!corrections.isEmpty()) {
      return result +
             "<tr><td valign='top' style='padding-bottom: 10px; padding-right: 5px; color: gray;'>" +
             GrazieBundle.message("grazie.settings.grammar.rule.correct") + "</td>" +
             "<td style='padding-bottom: 10px; width: 100%;'>" + visualizeSpace(corrections) + "</td></tr>";
    }
    return result;
  }

  private static List<MatchingResult> doCheck(TextContent text, List<ParsedSentence> sentences) {
    if (sentences.isEmpty()) return List.of();

    record Cached(List<ParsedSentence> sentences, List<MatchingResult> matches) {}

    AtomicReference<Cached> ref = CachedValuesManager.getManager(text.getContainingFile().getProject())
      .getCachedValue(text, () -> CachedValueProvider.Result.create(new AtomicReference<>(), HighlightingUtil.grazieConfigTracker()));

    try {
      Cached cached = ref.get();
      if (cached == null || !cached.sentences.equals(sentences)) {
        ParameterValues parameters = calcParameters(sentences);
        List<Tree> trees = ContainerUtil.map(sentences, s -> s.tree.withParameters(parameters));
        List<ai.grazie.rules.Rule> rules = enabledRules(trees.getFirst(), text);
        List<MatchingResult> matches = matchTrees(trees, rules);
        ref.set(cached = new Cached(sentences, matches));
      }
      return cached.matches;
    } catch (Throwable e) {
      Throwable cause = ExceptionUtil.getRootCause(e);
      if (cause instanceof ProcessCanceledException pce) {
        throw pce;
      }
      throw e;
    }
  }

  private static List<ai.grazie.rules.Rule> enabledRules(Tree sampleTree, TextContent content) {
    Language language = sampleTree.treeSupport().getGrazieLanguage();
    LanguageToolkit toolkit = LanguageToolkit.forLanguage(language);
    boolean flat = sampleTree.isFlat();
    return ContainerUtil.filter(toolkit.publishedRules(), r ->
      (!flat || r.supportsFlatTrees()) &&
      toGrazieRule(r).isCurrentlyEnabled(content)
    );
  }

  private static List<MatchingResult> matchTrees(List<Tree> trees, List<ai.grazie.rules.Rule> rules) {
    return ContainerUtil.map(trees, tree -> {
      List<MatchingResult> mrs = new ArrayList<>(rules.size());
      for (var rule : rules) {
        ProgressManager.checkCanceled();
        mrs.add(rule.match(List.of(tree)));
      }
      return MatchingResult.concat(mrs);
    });
  }

  private static ParameterValues calcParameters(List<ParsedSentence> sentences) {
    var parameters = new HashMap<String, String>();
    var ltLanguage = sentences.getFirst().tree.language();
    Language language = sentences.getFirst().tree.treeSupport().getGrazieLanguage();
    TextContent content = sentences.getFirst().extractedText;
    LanguageToolkit toolkit = LanguageToolkit.forLanguage(language);
    toolkit.allParameters(RuleIdeClient.INSTANCE).forEach(p -> parameters.put(p.id(), getParamValue(p, language, content)));
    if (language == Language.ENGLISH || language == Language.GERMAN) {
      String[] countries = ltLanguage.getCountries();
      if (countries.length > 0) {
        String variant = countries[0];
        if (language == Language.ENGLISH && variant.equals("GB") && GrazieConfig.Companion.get().getUseOxfordSpelling()) {
          variant = ChangeLanguageVariant.BRITISH_OXFORD_ID;
        }
        parameters.put(Parameter.LANGUAGE_VARIANT, variant);
      }
    }
    return new ParameterValues(parameters);
  }

  private static @Nullable String getParamValue(Parameter param, Language language, TextContent content) {
    TextStyleDomain domain = getTextDomain(content);
    String value = GrazieConfig.Companion.get().paramValue(domain, language, param);
    if (value == null || !ContainerUtil.exists(param.possibleValues(RuleIdeClient.INSTANCE), v -> value.equals(v.id()))) {
      TextStyle textStyle = TextStyle.styles(RuleIdeClient.INSTANCE).stream()
        .filter(it -> it.id().equals(domain.name()))
        .findFirst()
        .orElseGet(() -> GrazieConfig.Companion.get().getTextStyle());
      return param.defaultValue(textStyle, RuleIdeClient.INSTANCE).id();
    }
    return value;
  }

  public static List<TreeProblem> checkTextLevelProblems(PsiFile file) {
    List<SentenceWithContent> doc = obtainDocument(file);
    if (doc.isEmpty()) return List.of();

    List<TreeProblem> result = documentProblems(file, doc);
    List<TreeProblem> ignored = ContainerUtil.findAll(result, p -> findIgnoringFilter(p) != null);
    if (ignored.isEmpty()) {
      return result;
    }

    var docIgnoredRanges = ContainerUtil.map2Set(ignored, p ->
      ai.grazie.rules.tree.TextRange.spanRanges(p.match.reportedRanges().stream()));
    List<TreeProblem> secondPassResult = documentProblems(file, ContainerUtil.map(doc, swc -> new SentenceWithContent(
      swc.sentence.withSuppressions(union(swc.sentence.suppressions, sentenceRanges(docIgnoredRanges, swc.sentence, swc.docSentenceOffset))),
      swc.content,
      swc.contentStart,
      swc.docSentenceOffset)));
    return ContainerUtil.findAll(secondPassResult, p -> findIgnoringFilter(p) == null);
  }

  private static Set<ai.grazie.rules.tree.TextRange> union(Set<ai.grazie.rules.tree.TextRange> firstSet,
                                                           Set<ai.grazie.rules.tree.TextRange> secondSet) {
    Set<ai.grazie.rules.tree.TextRange> union = new LinkedHashSet<>(firstSet);
    union.addAll(secondSet);
    return union;
  }

  private static Set<ai.grazie.rules.tree.TextRange> sentenceRanges(Set<ai.grazie.rules.tree.TextRange> docRanges,
                                                                    DocumentSentence sentence,
                                                                    int sentenceOffset) {
    TextRange sentenceRange = TextRange.from(sentenceOffset, sentence.text.length());
    return StreamEx.of(docRanges)
      .filter(r -> sentenceRange.containsRange(r.start(), r.end()))
      .map(r -> r.shiftLeft(sentenceRange.getStartOffset()))
      .toSet();
  }

  private static List<TreeProblem> documentProblems(PsiFile file, List<SentenceWithContent> doc) {
    String docText = StreamEx.of(doc).map(swc -> swc.sentence.text).joining();
    List<TreeProblem> result = new ArrayList<>();

    MatchingResult mr = checkDocument(doc);
    for (RuleMatch match : mr.matches) {
      var reportedRanges = match.reportedRanges();
      var firstRange = reportedRanges.getFirst();
      SentenceWithContent sentence = findSentence(doc, firstRange.start(), firstRange.end());
      TextContent content = sentence.content;
      var fixes = asciiAwareFixes(match, content, docText);
      if (fixes == null) continue;

      var source = GrazieProblem.copyWithFixes(match.toProblem(RuleIdeClient.INSTANCE), List.of());
      var problem = new TreeProblem(source.withOffset(-sentence.contentStart), match, content);

      result.add(problem.withCustomFixes(ContainerUtil.map(fixes, fix ->
        new TextLevelFix(file, getQuickFixText(fix), fileLevelChanges(fix, doc))))
      );
    }
    return result;
  }

  private static List<StringOperation> fileLevelChanges(ProblemFix fix, List<SentenceWithContent> doc) {
    return Arrays.stream(fix.getChanges())
      .map(c -> {
        SentenceWithContent sentence = findSentence(doc, c.getRange().getStart(), c.getRange().getEndExclusive());
        return StringOperation.replace(sentence.content.textRangeToFile(ijRange(c).shiftLeft(sentence.contentStart)), c.getText());
      })
      .toList();
  }

  private static SentenceWithContent findSentence(List<SentenceWithContent> doc, int docStart, int docEnd) {
    ProgressManager.checkCanceled();
    var sentence = ContainerUtil.find(
      doc,
      s -> s.docSentenceOffset <= docStart && docEnd <= s.docSentenceOffset + s.sentence.text.length()
    );
    assert sentence != null;
    return sentence;
  }

  private static MatchingResult checkDocument(List<SentenceWithContent> doc) {
    Map<Language, List<ai.grazie.rules.Rule>> rules = new LinkedHashMap<>();
    for (SentenceWithContent ds : doc) {
      Language language = ds.sentence.language;
      if (!rules.containsKey(language) && StyleConfigurable.getRuleEngineLanguages().contains(language)) {
        rules.put(language, enabledRules(ds.sentence.treeOrThrow(), ds.content));
      }
    }

    return MatchingResult.concat(
      StreamEx.of(rules.values())
        .flatCollection(Function.identity())
        .filter(ai.grazie.rules.Rule::isStyleLike)
        .select(DocumentRule.class)
        .map(r -> {
          ProgressManager.checkCanceled();
          return r.checkDocument(ContainerUtil.map(doc, SentenceWithContent::sentence));
        })
    );
  }

  private static Map<String, Set<ai.grazie.rules.tree.TextRange>> suppressedRanges() {
    Map<String, Set<ai.grazie.rules.tree.TextRange>> result = new HashMap<>();
    int counter = 0;
    for (String pattern : GrazieConfig.Companion.get().getSuppressingContext().getSuppressed()) {
      int sep = pattern.indexOf('|');
      if (sep < 2) continue; // rule warnings cover at least two characters

      String fragment = pattern.substring(0, sep);
      String sentence = pattern.substring(sep + 1);
      int index = -1;
      while (true) {
        if (counter++ % 1024 == 0) ProgressManager.checkCanceled();

        index = sentence.indexOf(fragment, index + 1);
        if (index < 0) break;

        result.computeIfAbsent(sentence, k -> new HashSet<>()).add(ai.grazie.rules.tree.TextRange.fromLength(index, fragment.length()));
      }
    }
    return result;
  }

  private static List<SentenceWithContent> obtainDocument(PsiFile file) {
    var suppressedRanges = suppressedRanges();

    List<SentenceWithContent> doc = new ArrayList<>();
    int offset = 0;
    for (var entry : ParsedSentence.getAllCheckedSentences(file.getViewProvider()).entrySet()) {
      TextContent content = entry.getKey();
      List<ParsedSentence> sentences = entry.getValue();
      if (sentences.isEmpty()) continue;

      int offsetInContent = 0;

      List<MatchingResult> matches = doCheck(content, sentences);
      for (int i = 0; i < sentences.size(); i++) {
        ParsedSentence parsed = sentences.get(i);

        TextRange untrimmedRange = parsed.untrimmedRange;
        String untrimmedText = untrimmedRange.substring(content.toString());

        String trimmed = untrimmedText.trim();
        int trimmedStart = untrimmedText.indexOf(trimmed);
        var suppressions = ContainerUtil.map2Set(suppressedRanges.getOrDefault(trimmed, Set.of()), r -> r.shiftRight(trimmedStart));

        DocumentSentence.Analyzed ds = new DocumentSentence(untrimmedText, parsed.tree.treeSupport().getGrazieLanguage())
          .withIntro(i == 0 ? getIntro(content) : List.of())
          .withExclusions(SentenceTokenizer.rangeExclusions(content, untrimmedRange))
          .withSuppressions(suppressions)
          .withTree(parsed.tree.withStartOffset(offset))
          .withMetadata(matches.get(i).metadata);
        doc.add(new SentenceWithContent(ds, content, offset, offset + untrimmedRange.getStartOffset()));
        offsetInContent += ds.text.length();
      }
      offset += offsetInContent;
    }
    return doc;
  }

  private record SentenceWithContent(DocumentSentence.Analyzed sentence, TextContent content, int contentStart, int docSentenceOffset) {}

  private static List<Delimiter> getIntro(TextContent content) {
    List<Delimiter> intros = new ArrayList<>(List.of(Delimiter.fragmentBoundary));
    switch (content.getDomain()) {
      case COMMENTS -> intros.add(Delimiter.codeCommentStart);
      case DOCUMENTATION -> intros.add(Delimiter.codeDocumentationStart);
      case LITERALS -> intros.add(Delimiter.stringLiteralStart);
      default -> {}
    }
    return intros;
  }

  private static ProblemFilter findIgnoringFilter(TreeProblem p) {
    return ProblemFilter.allIgnoringFilters(p).findFirst().orElse(null);
  }

  static List<TreeProblem> check(TextContent text, List<ParsedSentence> sentences) {
    List<MatchingResult> mr = doCheck(text, sentences);
    List<TreeProblem> problems = checkPlainProblems(text, mr);
    AutoFix.consider(text, problems);
    return problems;
  }

  private static List<TreeProblem> checkPlainProblems(TextContent text, List<MatchingResult> matchingResults) {
    List<TreeProblem> problems = new ArrayList<>();
    for (RuleMatch match : ContainerUtil.flatMap(matchingResults, mr -> mr.matches)) {
      ProgressManager.checkCanceled();
      if (match instanceof NodeRuleMatch nrm && touchesUnknownFragments(text, nrm.result().touchedRange(), match.rule())) {
        continue;
      }

      ContainerUtil.addIfNotNull(problems, createProblem(text, match));
    }
    return problems;
  }

  private static @Nullable TreeProblem createProblem(TextContent text, RuleMatch match) {
    ai.grazie.rules.Rule rule = match.rule();
    if (shouldSuppressByPlace(rule, text)) {
      return null;
    }

    List<ProblemFix> fixes = asciiAwareFixes(match, text, text.toString());
    if (fixes == null) return null;

    if (rule.language() == Language.ENGLISH &&
        rule.id.equals("Typography.VARIANT_QUOTE_PUNCTUATION") &&
        text.getDomain() != TextDomain.PLAIN_TEXT &&
        match.reportedRanges().size() == 1 &&
        match.reportedRanges().getFirst().substring(text.toString()).matches(".*['\"]\\p{P}")) {
      return null;
    }

    return new TreeProblem(GrazieProblem.copyWithFixes(match.toProblem(RuleIdeClient.INSTANCE), fixes), match, text);
  }

  private static @Nullable List<ProblemFix> asciiAwareFixes(RuleMatch match, TextContent content, String fullText) {
    if (isAsciiContext(content)) {
      if (match.rule().id.endsWith(".ASCII_APPROXIMATIONS")) {
        // later these rules should be disabled by default in the corresponding writing style profiles
        return null;
      }
      try {
        return match.asciiContextFixes(fullText);
      } catch (StringIndexOutOfBoundsException e) {
        throw new RuntimeExceptionWithAttachments(e, toAttachment(content, fullText));
      }
    }
    return match.problemFixes();
  }

  private static Attachment toAttachment(TextContent content, String fullText) {
    PsiFile file = content.getContainingFile();
    return AttachmentFactory.createContext(
      "File type: " + file.getViewProvider().getVirtualFile().getFileType() + "\n" +
      "File language: " + file.getLanguage() + "\n" +
      "File name: " + file.getName() + "\n" +
      "Content: " + fullText
    );
  }

  private static boolean isAsciiContext(TextContent text) {
    return text.getDomain() != TextDomain.PLAIN_TEXT ||
           text.getContainingFile() instanceof PsiPlainTextFile;
  }

  private static boolean shouldSuppressByPlace(ai.grazie.rules.Rule rule, TextContent text) {
    TextStyleDomain domain = getTextDomain(text);
    if (domain == TextStyleDomain.Other) return false;
    return domain.getTextStyle().disabledRules().contains(rule.globalId());
  }

  private static boolean touchesUnknownFragments(TextContent text, ai.grazie.rules.tree.TextRange range, ai.grazie.rules.Rule rule) {
    var ruleRangeInText = ijRange(range);
    if (ruleRangeInText.getEndOffset() > text.length()) {
      LOG.error(
        "Invalid match range " + ruleRangeInText + " for rule " + rule + " in a text of length " + text.length(),
        new Attachment("text.txt", text.toString()));
      return true;
    }
    if (text.hasUnknownFragmentsIn(Text.expandToTouchWords(text, ruleRangeInText))) {
      return true;
    }
    return false;
  }

  public static class TreeProblem extends GrazieProblem {
    public final RuleMatch match;
    private final List<LocalQuickFix> customFixes;
    private final TextStyleDomain domain;

    TreeProblem(Problem problem, RuleMatch match, TextContent text) {
      this(problem, toGrazieRule(match.rule()), text, match, List.of());
    }

    private TreeProblem(Problem problem, com.intellij.grazie.text.@NotNull Rule ideaRule, @NotNull TextContent text,
                        RuleMatch match,
                        List<LocalQuickFix> customFixes) {
      super(problem, ideaRule, text);
      this.match = match;
      this.customFixes = customFixes;
      this.domain = getTextDomain(text);
    }

    @Override
    @SuppressWarnings("HardCodedStringLiteral")
    public @InspectionMessage @NotNull String getDescriptionTemplate(boolean isOnTheFly) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return match.rule().globalId();
      return super.getDescriptionTemplate(isOnTheFly);
    }

    @Override
    public @NotNull List<LocalQuickFix> getCustomFixes() {
      if (getSource().getActionSuggestions() == null) return customFixes;
      return ContainerUtil.concat(customFixes, ContainerUtil.mapNotNull(getSource().getActionSuggestions(), sug -> {
        if (sug instanceof ActionSuggestion.ChangeParameter parameter) {
          if (parameter.getParameterId().endsWith(Parameter.LANGUAGE_VARIANT)) {
            return ChangeLanguageVariant.create(
              match.rule().language(),
              Objects.requireNonNull(parameter.getSuggestedValue()).getId(),
              parameter.getQuickFixText()
            );
          }
          return new ConfigureSuggestedParameter(parameter, domain, match.rule().language(), parameter.getQuickFixText());
        }
        if (sug == ActionSuggestion.RephraseAround.INSTANCE) {
          return new RephraseAction();
        }
        return null;
      }));
    }

    public TreeProblem withCustomFixes(List<? extends LocalQuickFix> fixes) {
      return new TreeProblem(getSource(), getRule(), getText(), match, ContainerUtil.concat(customFixes, fixes));
    }

    @Override
    public boolean fitsGroup(@NotNull RuleGroup group) {
      Set<String> rules = group.getRules();
      SuppressableKind kind = getSource().getSuppressableKind();
      if (rules.contains(RuleGroup.INCOMPLETE_SENTENCE) && kind == SuppressableKind.INCOMPLETE_SENTENCE) {
        return true;
      }
      if (rules.contains(RuleGroup.SENTENCE_START_CASE) && kind == SuppressableKind.UPPERCASE_SENTENCE_START) {
        return true;
      }
      if (rules.contains(RuleGroup.SENTENCE_END_PUNCTUATION) && kind == SuppressableKind.UNFINISHED_SENTENCE) {
        return true;
      }
      if (rules.contains(RuleGroup.UNDECORATED_SENTENCE_SEPARATION) && kind == SuppressableKind.UNDECORATED_SENTENCE_SEPARATION) {
        return true;
      }
      if (rules.contains(RuleGroup.UNLIKELY_OPENING_PUNCTUATION) && kind == SuppressableKind.UNLIKELY_OPENING_PUNCTUATION) {
        return true;
      }

      return super.fitsGroup(group);
    }

    @Override
    public boolean isStyleLike() {
      return match.rule().isStyleLike();
    }

    @Override
    public boolean shouldSuppressInCodeLikeFragments() {
      return match.rule().shouldSuppressInCodeLikeFragments();
    }

    @Override
    public @NotNull TreeProblem copyWithProblemFixes(@NotNull List<ProblemFix> fixes) {
      return new TreeProblem(copyWithFixes(getSource(), fixes), getRule(), getText(), match, customFixes);
    }
  }
}
