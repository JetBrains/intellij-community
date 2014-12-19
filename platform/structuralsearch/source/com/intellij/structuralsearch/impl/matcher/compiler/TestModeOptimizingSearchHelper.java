package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiFile;

import java.util.Collections;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class TestModeOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private static String lastString;
  private final StringBuilder builder = new StringBuilder();
  private int lastLength;

  TestModeOptimizingSearchHelper(CompileContext _context) {
    super(_context);
  }

  public boolean doOptimizing() {
    return true;
  }

  public void clear() {
    lastString = builder.toString();
    builder.setLength(0);
    lastLength = 0;
  }

  protected void doAddSearchWordInCode(final String refname) {
    append(refname, "in code:");
  }

  @Override
  protected void doAddSearchWordInText(String refname) {
    append(refname, "in text:");
  }

  private void append(final String refname, final String str) {
    if (builder.length() == lastLength) builder.append("[");
    else builder.append("|");
    builder.append(str).append(refname);
  }

  protected void doAddSearchWordInComments(final String refname) {
    append(refname, "in comments:");
  }

  protected void doAddSearchWordInLiterals(final String refname) {
    append(refname, "in literals:");
  }

  public void endTransaction() {
    super.endTransaction();
    builder.append("]");
    lastLength = builder.length();
  }

  public boolean isScannedSomething() {
    return false;
  }

  public Set<PsiFile> getFilesSetToScan() {
    return Collections.emptySet();
  }

  public String getSearchPlan() {
    return lastString;
  }
}
