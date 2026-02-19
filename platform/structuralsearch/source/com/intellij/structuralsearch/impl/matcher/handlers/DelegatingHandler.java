package com.intellij.structuralsearch.impl.matcher.handlers;

public interface DelegatingHandler {
  MatchingHandler getDelegate();
}
