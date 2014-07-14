package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;

import java.util.Set;

public abstract class MatchPredicateProvider {
  public static final ExtensionPointName<MatchPredicateProvider> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.matchPredicateProvider");
  public abstract void collectPredicates(MatchVariableConstraint constraint,
                                         String name,
                                         MatchOptions options,
                                         Set<MatchPredicate> predicates);
}
