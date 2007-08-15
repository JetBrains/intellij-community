package com.intellij.execution.junit2.info;


public interface TestInfo extends PsiLocator {
  String getComment();
  String getName();
  boolean shouldRun();
  int getTestsCount();
}
