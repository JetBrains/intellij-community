// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
class TestModeOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private final StringBuilder builder = new StringBuilder();
  private String plan;
  private boolean myTransactionStarted = false;

  private final List<String> myWords = new SmartList<>();

  @Override
  public boolean doOptimizing() {
    return true;
  }

  @Override
  public void clear() {
    assert !myTransactionStarted;
    plan = builder.toString();
    builder.setLength(0);
  }

  private void append(final String word, final String prefix) {
    myWords.add(prefix + word);
    myTransactionStarted = true;
  }

  @Override
  protected void doAddSearchWordInCode(@NotNull String word) {
    append(word, "in code:");
  }

  @Override
  protected void doAddSearchWordInText(@NotNull String word) {
    append(word, "in text:");
  }

  @Override
  protected void doAddSearchWordInComments(@NotNull String word) {
    append(word, "in comments:");
  }

  @Override
  protected void doAddSearchWordInLiterals(@NotNull String word) {
    append(word, "in literals:");
  }

  @Override
  public void endTransaction() {
    if (!myTransactionStarted) return;
    myTransactionStarted = false;
    super.endTransaction();
    Collections.sort(myWords); // to ensure stable ordering
    builder.append('[');
    boolean bar = false;
    for (String word : myWords) {
      if (bar) builder.append('|');
      else bar = true;
      builder.append(word);
    }
    this.builder.append(']');
    myWords.clear();
  }

  @Override
  public boolean isScannedSomething() {
    return false;
  }

  @NotNull
  @Override
  public Set<PsiFile> getFilesSetToScan() {
    assert !myTransactionStarted;
    return Collections.emptySet();
  }

  public String getSearchPlan() {
    assert !myTransactionStarted;
    return plan;
  }
}
