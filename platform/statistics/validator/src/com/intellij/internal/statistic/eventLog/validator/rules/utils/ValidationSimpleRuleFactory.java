// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.utils;

import com.intellij.internal.statistic.eventLog.util.ValidatorStringUtil;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRegexpAwareRule;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ValidationSimpleRuleFactory {
  public static final String UTIL_PREFIX = "util#";
  private static final String START = "{";
  private static final String END = "}";
  private static final BooleanRuleProducer RULE_PRODUCER = new BooleanRuleProducer();
  private static final EnumRuleProducer ENUM_RULE_PRODUCER = new EnumRuleProducer();
  private static final EnumReferenceRuleProducer ENUM_REFERENCE_RULE_PRODUCER = new EnumReferenceRuleProducer();
  private static final RegexpRuleProducer REGEXP_RULE_PRODUCER = new RegexpRuleProducer();
  private static final RegexpReferenceRuleProducer REGEXP_REFERENCE_RULE_PRODUCER = new RegexpReferenceRuleProducer();
  public static final UtilRuleProducer REJECTING_UTIL_URL_PRODUCER = new RejectingUtilRuleProducer();

  private final UtilRuleProducer myUtilRuleProducer;

  private static final FUSRule UNPARSED_EXPRESSION = (s, c) -> ValidationResultType.INCORRECT_RULE;

  public ValidationSimpleRuleFactory(@NotNull UtilRuleProducer utilRuleProducer) {
    myUtilRuleProducer = utilRuleProducer;
  }

  public ValidationSimpleRuleFactory() {
    this(REJECTING_UTIL_URL_PRODUCER);
  }

  public FUSRule @NotNull [] getRules(@Nullable String key, @Nullable Set<String> rules,
                                      @NotNull EventGroupContextData contextData) {
    if (rules == null) return FUSRule.EMPTY_ARRAY;
    List<FUSRule> fusRules = new ArrayList<>();
    for (String rule : rules) {
      fusRules.add(createRule(rule, contextData));
    }
    fusRules.sort(getRulesComparator());
    return fusRules.toArray(FUSRule.EMPTY_ARRAY);
  }

  private static @NotNull Comparator<FUSRule> getRulesComparator() {
    // todo: do it better )))
    return (o1, o2) -> {
      if (o1 instanceof EnumValidationRule) return o2 instanceof EnumValidationRule ? -1 : 0;
      return o2 instanceof EnumValidationRule ? 0 : 1;
    };
  }

  @NotNull
  public FUSRule createRule(@NotNull String rule,
                            @NotNull EventGroupContextData contextData) {
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
  private FUSRule createSimpleRule(@NotNull String rule, @NotNull EventGroupContextData contextData) {
    return createSimpleRule(rule, contextData,
                            RULE_PRODUCER,
                            myUtilRuleProducer,
                            ENUM_RULE_PRODUCER,
                            ENUM_REFERENCE_RULE_PRODUCER,
                            REGEXP_RULE_PRODUCER,
                            REGEXP_REFERENCE_RULE_PRODUCER);
  }


  private static FUSRule createSimpleRule(@NotNull String rule,
                                          @NotNull EventGroupContextData contextData,
                                          ValidationRuleProducer... ruleProducers) {
    for (ValidationRuleProducer builder : ruleProducers) {
      String prefix = builder.getPrefix();
      if (rule.startsWith(prefix)) {
        String value = rule.substring(prefix.length());
        if (!ValidatorStringUtil.isEmpty(value)) {
          return builder.createValidationRule(value, contextData);
        }
      }
    }
    return null;
  }

  @NotNull
  private FUSRule createExpressionRule(@NotNull String rule, @NotNull EventGroupContextData contextData) {
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
    return createExpressionValidationRule(rule, contextData);
  }

  @NotNull
  private FUSRule createExpressionValidationRule(@NotNull String rule, @NotNull EventGroupContextData contextData) {
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
        sb.append(RegexpValidationRule.escapeText(node));
      }
    }
    return new RegexpValidationRule(sb.toString());
  }

  // 'aaaaa{util#foo_util}bbbb' = > prefix='aaaaa', suffix='bbbb',  utilRule = createRule('{util#foo_util}')
  private FUSRule createExpressionUtilRule(@NotNull List<String> nodes) {
    FUSRule fusRule = null;
    String suffix = "";
    String prefix = "";
    boolean utilNodeFound = false;
    for (String string : nodes) {
      if (isExpressionNode(string)) {
        if (!string.contains(UTIL_PREFIX)) return UNPARSED_EXPRESSION;

        FUSRule simpleRule = createRule(unwrapRuleNode(string), EventGroupContextData.EMPTY);
        if (simpleRule instanceof UtilValidationRule) {
          fusRule = simpleRule;
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
    return new ExpressionValidationRule(fusRule, prefix, suffix);
  }

  @NotNull
  // 'abc.{enum:abc}.foo.{enum:foo}.ddd' => {'abc.', '{enum:abc}', '.foo.', '{enum:foo}', '.ddd'}
  // if (could not be parsed) return Collections.emptyList()
  public static List<String> parseSimpleExpression(@NotNull String s) {
    int currentRuleStart = s.indexOf(START);
    if (ValidatorStringUtil.isEmptyOrSpaces(s)) return Collections.emptyList();
    if (currentRuleStart == -1) return Collections.singletonList(s);
    int lastRuleEnd = -1;

    final List<String> nodes = new ArrayList<>();
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
    if (!ValidatorStringUtil.isEmpty(s)) nodes.add(s);
  }

  private static boolean isExpressionNode(@NotNull String node) {
    return node.startsWith(START) && node.endsWith(END);
  }

  @NotNull
  private static String unwrapRuleNode(@NotNull String rule) {
    return isExpressionNode(rule) ? rule.substring(START.length(), rule.length() - END.length()) : rule;
  }
}
