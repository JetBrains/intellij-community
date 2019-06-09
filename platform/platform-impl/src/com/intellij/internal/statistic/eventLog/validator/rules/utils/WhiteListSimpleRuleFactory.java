// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRegexpAwareRule;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.WhiteListGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.*;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class WhiteListSimpleRuleFactory {
  private static final String RULE_PREFIX = "rule:";          // rule:TRUE , rule:FALSE
  private static final String ENUM_PREFIX = "enum:";          // enum:A|B|C
  private static final String REGEXP_PREFIX = "regexp:";      // regexp:\d+
  private static final String ENUM_REF_PREFIX = "enum#";     //  enum#<ref-id>
  private static final String REGEXP_REF_PREFIX = "regexp#"; //  regexp#<ref-id>
  private static final String UTIL_PREFIX = "util#";
  private static final String ENUM_SEPARATOR = "|";
  private static final String START = "{";
  private static final String END = "}";

  private static final FUSRule UNPARSED_EXPRESSION = (s,c) -> ValidationResultType.INCORRECT_RULE;

  @NotNull
  public static FUSRule createRule(@NotNull String rule) {
    return createRule(rule, WhiteListGroupContextData.EMPTY);
  }

  @NotNull
  public static FUSRule createRule(@NotNull String rule,
                                   @NotNull WhiteListGroupContextData contextData) {
    // 1. enum:<value> or {enum:<value>}   => enum:A|B|C
    // 2. enum#<ref-id> or {enum#<ref-id>} => enum#my-enum
    // 3. regexp:<value> or {regexp:<value>} => regexp:0|[1-9][0-9]*
    // 4. regexp#<ref-id> or {regexp#<ref-id>} => regexp#my-regexp
    // 5. util#<util-id>
    // 6. abc.{enum:abc}.foo.{enum:foo}.ddd
    // 7. {rule:TRUE}
    // 8. {rule:FALSE}
    FUSRule wlr = createSimpleRule(rule.trim(), contextData);
    return wlr != null ? wlr : createExpressionRule(rule.trim(), contextData);
  }

  @Nullable
  private static FUSRule createSimpleRule(@NotNull String rule, @NotNull WhiteListGroupContextData contextData) {

    return createSimpleRule(rule,
                            Pair.create(RULE_PREFIX,s -> getBooleanRule(s)),
                            Pair.create(UTIL_PREFIX,s -> getCustomUtilRule(s)),
                            Pair.create(ENUM_PREFIX, s -> new EnumWhiteListRule(StringUtil.split(s, ENUM_SEPARATOR, true, false))),
                            Pair.create(ENUM_REF_PREFIX, s -> new EnumWhiteListRule(contextData.getEnum(s))),
                            Pair.create(REGEXP_PREFIX, s -> new RegexpWhiteListRule(s)),
                            Pair.create(REGEXP_REF_PREFIX, s -> new RegexpWhiteListRule(contextData.getRegexp(s)))); }

  @Nullable
  private static CustomWhiteListRule getCustomUtilRule(String s) {
    for (CustomWhiteListRule extension : CustomWhiteListRule.EP_NAME.getExtensions()) {
      if (isDevelopedByJetBrains(extension) && extension.acceptRuleId(s)) return extension;
    }

    return null;
  }

  private static boolean isDevelopedByJetBrains(CustomWhiteListRule extension) {
    return ApplicationManager.getApplication().isUnitTestMode() || PluginInfoDetectorKt.getPluginInfo(extension.getClass()).isDevelopedByJetBrains();
  }

  @Nullable
  private static FUSRule getBooleanRule(@Nullable String value) {
    if ("TRUE".equals(value)) return FUSRule.TRUE;
    if ("FALSE".equals(value)) return FUSRule.FALSE;

    return null;
  }

  @Nullable
  private static FUSRule createSimpleRule(@NotNull String rule, Pair<String, Function<String, FUSRule>>... rules) {
    for (Pair<String, Function<String, FUSRule>> pair : rules) {
      if (rule.startsWith(pair.first)) {
        String value = rule.substring(pair.first.length());
        if (StringUtil.isNotEmpty(value)) {
          return pair.second.fun(value);
        }
      }
    }
    return null;
  }

  @NotNull
  private static FUSRule createExpressionRule(@NotNull String rule,
                                              @NotNull WhiteListGroupContextData contextData) {
    List<String> nodes = parseSimpleExpression(rule);
    if (nodes.size() == 1) {
      String n = nodes.get(0);
      if (n.contains(START)) {
        FUSRule simpleRule = createSimpleRule(unwrapRuleNode(n), contextData);
        if (simpleRule != null) return simpleRule;
      }
    }

    if (rule.contains(UTIL_PREFIX)) {
      return createExpressionUtilRule(nodes);
    }
    return createExpressionWhiteListRule(rule, contextData);
  }

  @NotNull
  private static FUSRule createExpressionWhiteListRule(@NotNull String rule, @NotNull WhiteListGroupContextData contextData) {
    StringBuilder sb = new StringBuilder();
    for (String node : parseSimpleExpression(rule)) {
      if (isExpressionNode(node)) {
        FUSRule fusRule = createRule(unwrapRuleNode(node), contextData);
        if (fusRule instanceof FUSRegexpAwareRule) {
          sb.append("(");
          sb.append(((FUSRegexpAwareRule)fusRule).asRegexp());
          sb.append(")");
        }
        else {
          return UNPARSED_EXPRESSION;
        }
      }
      else {
        sb.append(RegexpWhiteListRule.escapeText(node));
      }
    }
    return new RegexpWhiteListRule(sb.toString());
  }

  // 'aaaaa{util#foo_util}bbbb' = > prefix='aaaaa', suffix='bbbb',  utilRule = createRule('{util#foo_util}')
  private static FUSRule createExpressionUtilRule(@NotNull List<String> nodes) {
    FUSRule fusRule = null;
    String suffix = "";
    String prefix = "";
    boolean utilNodeFound = false;
    for (String string : nodes) {
      if (isExpressionNode(string)) {
        if (!string.contains(UTIL_PREFIX)) return UNPARSED_EXPRESSION;

        FUSRule simpleRule = createRule(unwrapRuleNode(string));
        if (simpleRule instanceof CustomWhiteListRule) {
          fusRule = (CustomWhiteListRule)simpleRule;
        }
        else {
          return UNPARSED_EXPRESSION;
        }
        utilNodeFound = true;
      }
      else {
        if (utilNodeFound) {
          suffix = string;
        }
        else {
          prefix = string;
        }
      }
    }
    if (fusRule == null) return UNPARSED_EXPRESSION;
    return new UtilExpressionWhiteListRule(fusRule, prefix, suffix);
  }

  @NotNull
  // 'abc.{enum:abc}.foo.{enum:foo}.ddd' => {'abc.', '{enum:abc}', '.foo.', '{enum:foo}', '.ddd'}
  // if (could not be parsed) return Collections.emptyList()
  public static List<String> parseSimpleExpression(@NotNull String s) {
    int currentRuleStart = s.indexOf(START);
    if (StringUtil.isEmptyOrSpaces(s)) return Collections.emptyList();
    if (currentRuleStart == -1) return Collections.singletonList(s);
    int lastRuleEnd = -1;

    final List<String> nodes = ContainerUtil.newSmartList();
    if (currentRuleStart > 0) addNonEmpty(nodes, s.substring(0, currentRuleStart));

    while (currentRuleStart >= 0) {
      int currentRuleEnd = s.indexOf(END, currentRuleStart);
      if (currentRuleEnd == -1) return Collections.emptyList();
      lastRuleEnd = currentRuleEnd + END.length();

      // check invalid '{aaa{bb}'
      int nextRule = s.indexOf(START, currentRuleStart + START.length());
      if (nextRule > 0 && nextRule < lastRuleEnd) return Collections.emptyList();

      addNonEmpty(nodes, s.substring(currentRuleStart, lastRuleEnd));
      currentRuleStart = s.indexOf(START, lastRuleEnd);

      if (currentRuleStart > 0) addNonEmpty(nodes, s.substring(lastRuleEnd, currentRuleStart));
    }
    if (lastRuleEnd > 0) addNonEmpty(nodes, s.substring(lastRuleEnd));
    return nodes;
  }

  private static void addNonEmpty(@NotNull List<? super String> nodes, @Nullable String s) {
    if (StringUtil.isNotEmpty(s)) nodes.add(s);
  }

  private static boolean isExpressionNode(@NotNull String node) {
    return node.startsWith(START) && node.endsWith(END);
  }

  @NotNull
  private static String unwrapRuleNode(@NotNull String rule) {
    return isExpressionNode(rule) ? rule.substring(START.length(), rule.length() - END.length()) : rule;
  }
}
