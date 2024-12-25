// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.KEEP;

public abstract class AbstractRearrangerTest extends BasePlatformTestCase {
  private static final RichTextHandler[] RICH_TEXT_HANDLERS = {new RangeHandler(), new FoldingHandler()};
  private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([^\\s]+)=([^\\s]+)");

  protected FileType fileType;
  protected Language language;

  protected @NotNull CommonCodeStyleSettings getCommonSettings() {
    return CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(language);
  }

  protected static ArrangementSectionRule section(StdArrangementMatchRule @NotNull ... rules) {
    return section(null, null, rules);
  }

  protected static ArrangementSectionRule section(@Nullable String start, @Nullable String end, StdArrangementMatchRule @NotNull ... rules) {
    return ArrangementSectionRule.create(start, end, rules);
  }

  protected static StdArrangementRuleAliasToken alias(@NotNull String id, StdArrangementMatchRule @NotNull ... rules) {
    return new StdArrangementRuleAliasToken(id, id, List.of(rules));
  }

  protected static @NotNull ArrangementGroupingRule group(@NotNull ArrangementSettingsToken type) {
    return group(type, KEEP);
  }

  protected static @NotNull ArrangementGroupingRule group(@NotNull ArrangementSettingsToken type, @NotNull ArrangementSettingsToken order) {
    return new ArrangementGroupingRule(type, order);
  }

  protected static @NotNull StdArrangementMatchRule rule(@NotNull ArrangementSettingsToken token) {
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(atom(token)));
  }

  protected static @NotNull StdArrangementMatchRule nameRule(@NotNull String nameFilter, ArrangementSettingsToken @NotNull ... tokens) {
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

  protected static @NotNull StdArrangementMatchRule rule(ArrangementSettingsToken @NotNull ... conditions) {
    return rule(ContainerUtil.map(conditions, it -> atom(it)));
  }

  protected static @NotNull StdArrangementMatchRule rule(@NotNull List<ArrangementAtomMatchCondition> conditions) {
    return rule(conditions.toArray(new ArrangementAtomMatchCondition[0]));
  }

  protected static @NotNull StdArrangementMatchRule rule(ArrangementAtomMatchCondition @NotNull ... conditions) {
    ArrangementMatchCondition compositeCondition = ArrangementUtil.combine(conditions);
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(compositeCondition));
  }

  protected static @NotNull StdArrangementMatchRule ruleWithOrder(@NotNull ArrangementSettingsToken orderType, @NotNull StdArrangementMatchRule rule) {
    return new StdArrangementMatchRule(rule.getMatcher(), orderType);
  }

  protected static @NotNull ArrangementAtomMatchCondition atom(@NotNull ArrangementSettingsToken token) {
    return new ArrangementAtomMatchCondition(token);
  }

  protected static ArrangementAtomMatchCondition atom(@NotNull ArrangementSettingsToken token, boolean included) {
    return new ArrangementAtomMatchCondition(token, included);
  }

  protected static @NotNull ArrangementAtomMatchCondition atom(@NotNull String nameFilter) {
    return new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter);
  }
  
  protected void doTest(@NotNull String initial, @NotNull String expected, @NotNull List<?> rules) {
    doTest(initial, expected, rules, List.of());
  }
  
  protected void doTest(@NotNull String initial, @NotNull String expected, @NotNull List<?> rules, 
                        @NotNull List<ArrangementGroupingRule> groups) {
    List<ArrangementSectionRule> sectionRules = getSectionRules(rules);
    StdArrangementSettings arrangementSettings = new StdArrangementSettings(groups, sectionRules);
    doTestWithSettings(initial, expected, arrangementSettings, null);
  }

  protected void doTest(@NotNull Map<String, ?> args) {
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

    doTestWithSettings(text, expected, arrangementSettings, ranges);
  }

  protected void doTestWithSettings(@NotNull String text,
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

    myFixture.configureByText(fileType, info.text);

    final FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
    for (final FoldingInfo foldingInfo : info.foldings) {
      foldingModel.runBatchFoldingOperation(() -> {
        FoldRegion region = foldingModel.addFoldRegion(foldingInfo.start(), foldingInfo.end(), foldingInfo.placeholder());
        if (region != null) region.setExpanded(false);
      });
    }

    if (arrangementSettings != null) {
      CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(language);
      settings.setArrangementSettings(arrangementSettings);
    }
    ArrangementEngine engine = ArrangementEngine.getInstance();
    CommandProcessor.getInstance().executeCommand(getProject(), ()-> engine.arrange(myFixture.getEditor(), myFixture.getFile(), info.ranges), null, null);


    // Check expectation.
    Info after = parse(expected);
    assertEquals(after.text, myFixture.getEditor().getDocument().getText());
    for (FoldingInfo it : after.foldings) {
      FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(it.start());
      assertNotNull("Expected to find fold region at offset " + it.start(), foldRegion);
      assertEquals(it.end(), foldRegion.getEndOffset());
    }
  }

  protected @NotNull @Unmodifiable List<ArrangementSectionRule> getSectionRules(@Nullable List<?> rules) {
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
