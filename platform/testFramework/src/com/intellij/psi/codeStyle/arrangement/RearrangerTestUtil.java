// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementExtendableSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementRuleAliasToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.KEEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Rearranger checks and arrangement rule builders for a JUnit 5 test.
 * This is the replacement of {@link AbstractRearrangerTest}.
 */
@TestOnly
public final class RearrangerTestUtil {
  private static final RichTextHandler[] RICH_TEXT_HANDLERS = {new RangeHandler(), new FoldingHandler()};
  private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([^\\s]+)=([^\\s]+)");

  private RearrangerTestUtil() {
  }

  /**
   * Returns the common code style settings of {@code language} in {@code project}.
   * Call it from any thread.
   */
  public static @NotNull CommonCodeStyleSettings getCommonSettings(@NotNull Project project, @NotNull Language language) {
    return CodeStyle.getSettings(project).getCommonSettings(language);
  }

  /**
   * Creates a section rule with no start and no end comment.
   * Call it from any thread.
   */
  public static ArrangementSectionRule section(StdArrangementMatchRule @NotNull ... rules) {
    return section(null, null, rules);
  }

  /**
   * Creates a section rule with the given start and end comments.
   * Call it from any thread.
   */
  public static ArrangementSectionRule section(@Nullable String start, @Nullable String end, StdArrangementMatchRule @NotNull ... rules) {
    return ArrangementSectionRule.create(start, end, rules);
  }

  /**
   * Creates a rule alias token with {@code id} as its ID and its name.
   * Call it from any thread.
   */
  public static StdArrangementRuleAliasToken alias(@NotNull String id, StdArrangementMatchRule @NotNull ... rules) {
    return new StdArrangementRuleAliasToken(id, id, List.of(rules));
  }

  /**
   * Creates a grouping rule with the {@link StdArrangementTokens.Order#KEEP} order.
   * Call it from any thread.
   */
  public static @NotNull ArrangementGroupingRule group(@NotNull ArrangementSettingsToken type) {
    return group(type, KEEP);
  }

  /**
   * Creates a grouping rule with the given order.
   * Call it from any thread.
   */
  public static @NotNull ArrangementGroupingRule group(@NotNull ArrangementSettingsToken type, @NotNull ArrangementSettingsToken order) {
    return new ArrangementGroupingRule(type, order);
  }

  /**
   * Creates a match rule for one token.
   * Call it from any thread.
   */
  public static @NotNull StdArrangementMatchRule rule(@NotNull ArrangementSettingsToken token) {
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(atom(token)));
  }

  /**
   * Creates a match rule for a name regular expression and the given tokens.
   * Call it from any thread.
   */
  public static @NotNull StdArrangementMatchRule nameRule(@NotNull String nameFilter, ArrangementSettingsToken @NotNull ... tokens) {
    if (tokens.length == 0) {
      return new StdArrangementMatchRule(new StdArrangementEntryMatcher(atom(nameFilter)));
    }
    else {
      ArrangementAtomMatchCondition[] conditions = new ArrangementAtomMatchCondition[tokens.length + 1];
      conditions[0] = atom(nameFilter);
      for (int i = 0; i < tokens.length; i++) conditions[i + 1] = atom(tokens[i]);
      return rule(conditions);
    }
  }

  /**
   * Creates a match rule that matches all the given tokens.
   * Call it from any thread.
   */
  public static @NotNull StdArrangementMatchRule rule(ArrangementSettingsToken @NotNull ... conditions) {
    return rule(ContainerUtil.map(conditions, it -> atom(it)));
  }

  /**
   * Creates a match rule that matches all the given conditions.
   * Call it from any thread.
   */
  public static @NotNull StdArrangementMatchRule rule(@NotNull List<ArrangementAtomMatchCondition> conditions) {
    return rule(conditions.toArray(new ArrangementAtomMatchCondition[0]));
  }

  /**
   * Creates a match rule that matches all the given conditions.
   * Call it from any thread.
   */
  public static @NotNull StdArrangementMatchRule rule(ArrangementAtomMatchCondition @NotNull ... conditions) {
    ArrangementMatchCondition compositeCondition = ArrangementUtil.combine(conditions);
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(compositeCondition));
  }

  /**
   * Creates a copy of {@code rule} with the given order.
   * Call it from any thread.
   */
  public static @NotNull StdArrangementMatchRule ruleWithOrder(@NotNull ArrangementSettingsToken orderType, @NotNull StdArrangementMatchRule rule) {
    return new StdArrangementMatchRule(rule.getMatcher(), orderType);
  }

  /**
   * Creates an atom condition for one token.
   * Call it from any thread.
   */
  public static @NotNull ArrangementAtomMatchCondition atom(@NotNull ArrangementSettingsToken token) {
    return new ArrangementAtomMatchCondition(token);
  }

  /**
   * Creates an atom condition for one token. When {@code included} is {@code false}, the condition matches the entries without the token.
   * Call it from any thread.
   */
  public static ArrangementAtomMatchCondition atom(@NotNull ArrangementSettingsToken token, boolean included) {
    return new ArrangementAtomMatchCondition(token, included);
  }

  /**
   * Creates an atom condition for a name regular expression.
   * Call it from any thread.
   */
  public static @NotNull ArrangementAtomMatchCondition atom(@NotNull String nameFilter) {
    return new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter);
  }

  /**
   * Runs {@link #doTest(CodeInsightTestFixture, FileType, Language, String, String, List, List)} with no grouping rules.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull FileType fileType,
                            @NotNull Language language,
                            @NotNull String initial,
                            @NotNull String expected,
                            @NotNull List<?> rules) {
    doTest(fixture, fileType, language, initial, expected, rules, List.of());
  }

  /**
   * Runs {@link #doTestWithSettings} with the given match rules and grouping rules.
   * An item of {@code rules} is an {@link ArrangementSectionRule} or a {@link StdArrangementMatchRule}.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull FileType fileType,
                            @NotNull Language language,
                            @NotNull String initial,
                            @NotNull String expected,
                            @NotNull List<?> rules,
                            @NotNull List<ArrangementGroupingRule> groups) {
    List<ArrangementSectionRule> sectionRules = getSectionRules(rules);
    StdArrangementSettings arrangementSettings = new StdArrangementSettings(groups, sectionRules);
    doTestWithSettings(fixture, fileType, language, initial, expected, arrangementSettings, null);
  }

  /**
   * Runs {@link #doTestWithSettings} with the settings from {@code args}.
   * The keys are {@code initial}, {@code expected}, {@code rules}, {@code groups}, {@code aliases}, and {@code ranges}.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull FileType fileType,
                            @NotNull Language language,
                            @NotNull Map<String, ?> args) {
    @SuppressWarnings("unchecked") List<ArrangementGroupingRule> groupingRules =
      ObjectUtils.coalesce((List<ArrangementGroupingRule>)args.get("groups"), Collections.emptyList());

    List<?> rules = (List<?>)args.get("rules");
    List<ArrangementSectionRule> sectionRules = getSectionRules(rules);

    @SuppressWarnings("unchecked")
    List<StdArrangementRuleAliasToken> aliases =
      ObjectUtils.coalesce((List<StdArrangementRuleAliasToken>)args.get("aliases"), Collections.emptyList());

    final StdArrangementSettings arrangementSettings = new StdArrangementExtendableSettings(groupingRules, sectionRules, aliases);

    String text = (String)args.get("initial");
    String expected = (String)args.get("expected");
    @SuppressWarnings("unchecked") List<TextRange> ranges = (List<TextRange>)args.get("ranges");

    doTestWithSettings(fixture, fileType, language, text, expected, arrangementSettings, ranges);
  }

  /**
   * Configures {@code text} as a file of {@code fileType}, runs the arrangement engine, and checks the result against {@code expected}.
   * The text can mark the ranges to arrange with {@code <range>} tags and the collapsed fold regions with {@code <fold>} tags.
   * When {@code arrangementSettings} is not {@code null}, the method sets it for {@code language} in the project code style settings.
   * When the text and {@code ranges} give no range, the whole text is arranged.
   * Call it from any thread.
   */
  public static void doTestWithSettings(@NotNull CodeInsightTestFixture fixture,
                                        @NotNull FileType fileType,
                                        @NotNull Language language,
                                        @NotNull String text,
                                        @NotNull String expected,
                                        @Nullable ArrangementSettings arrangementSettings,
                                        @Nullable List<TextRange> ranges) {
    Info info = parse(text);
    if (!isEmpty(ranges) && !isEmpty(info.ranges)) {
      fail("Duplicate ranges set: explicit: " + ranges + ", " + "derived: " + info.ranges + ", text:\n" + text);
    }
    if (isEmpty(info.ranges)) {
      info.ranges = !isEmpty(ranges) ? ranges : Collections.singletonList(TextRange.from(0, text.length()));
    }

    fixture.configureByText(fileType, info.text);

    if (arrangementSettings != null) {
      getCommonSettings(fixture.getProject(), language).setArrangementSettings(arrangementSettings);
    }

    Info after = parse(expected);
    Editor editor = fixture.getEditor();
    PsiFile file = fixture.getFile();
    List<Integer> foldRegionEnds = new ArrayList<>();
    String actualText = EdtTestUtil.runInEdtAndGet(() -> {
      FoldingModel foldingModel = editor.getFoldingModel();
      for (FoldingInfo foldingInfo : info.foldings) {
        foldingModel.runBatchFoldingOperation(() -> {
          FoldRegion region = foldingModel.addFoldRegion(foldingInfo.start(), foldingInfo.end(), foldingInfo.placeholder());
          if (region != null) region.setExpanded(false);
        });
      }

      ArrangementEngine engine = ArrangementEngine.getInstance();
      CommandProcessor.getInstance().executeCommand(fixture.getProject(), () -> engine.arrange(editor, file, info.ranges), null, null);

      for (FoldingInfo it : after.foldings) {
        FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(it.start());
        foldRegionEnds.add(foldRegion == null ? null : foldRegion.getEndOffset());
      }
      return editor.getDocument().getText();
    });

    // Check expectation.
    assertEquals(after.text, actualText);
    for (int i = 0; i < after.foldings.size(); i++) {
      FoldingInfo it = after.foldings.get(i);
      Integer foldRegionEnd = foldRegionEnds.get(i);
      assertNotNull(foldRegionEnd, "Expected to find fold region at offset " + it.start());
      assertEquals(it.end(), foldRegionEnd);
    }
  }

  /**
   * Converts each {@link StdArrangementMatchRule} of {@code rules} to an {@link ArrangementSectionRule}.
   * Returns an empty list for {@code null}.
   * Call it from any thread.
   */
  public static @NotNull @Unmodifiable List<ArrangementSectionRule> getSectionRules(@Nullable List<?> rules) {
    if (rules == null) {
      return ContainerUtil.emptyList();
    }
    return ContainerUtil.map(rules, o -> o instanceof ArrangementSectionRule
                                         ? (ArrangementSectionRule)o
                                         : ArrangementSectionRule.create((StdArrangementMatchRule)o));
  }

  private static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  private static @NotNull Info parse(@NotNull String text) {
    Info result = new Info();
    StringBuilder buffer = new StringBuilder(text);

    int offset = 0;
    while (offset < buffer.length()) {
      RichTextHandler handler = null;
      int richTextMarkStart = -1;
      for (RichTextHandler h : RICH_TEXT_HANDLERS) {
        int i = buffer.indexOf("<" + h.getMarker(), offset);
        if (i >= 0 && (handler == null || i < richTextMarkStart)) {
          richTextMarkStart = i;
          handler = h;
        }
      }
      if (handler == null) break;

      String marker = handler.getMarker();
      int attrStart = richTextMarkStart + marker.length() + 1;
      int openingTagEnd = buffer.indexOf(">", richTextMarkStart);
      int openTagLength = openingTagEnd - richTextMarkStart + 1;
      Map<String, String> attributes = parseAttributes(buffer.substring(attrStart, openingTagEnd));

      String closingTag = "</" + marker + ">";
      int closingTagStart = buffer.indexOf(closingTag);
      assert closingTagStart > 0;

      handler.handle(result, attributes, richTextMarkStart, closingTagStart - openTagLength);
      buffer.delete(closingTagStart, closingTagStart + closingTag.length());
      buffer.delete(richTextMarkStart, openingTagEnd + 1);
      offset = closingTagStart - openTagLength;
    }

    result.text = buffer.toString();
    return result;
  }

  private static @NotNull Map<String, String> parseAttributes(@NotNull String text) {
    if (text.isEmpty()) return Collections.emptyMap();
    Matcher matcher = ATTRIBUTE_PATTERN.matcher(text);
    Map<String, String> result = new LinkedHashMap<>();
    while (matcher.find()) result.put(matcher.group(1), matcher.group(2));
    return result;
  }
}
