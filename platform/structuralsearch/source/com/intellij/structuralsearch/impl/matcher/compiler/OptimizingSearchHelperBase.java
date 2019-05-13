// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
*/
abstract class OptimizingSearchHelperBase implements OptimizingSearchHelper {
  private final THashSet<String> scanned;
  private final THashSet<String> scannedText;
  private final THashSet<String> scannedComments;
  private final THashSet<String> scannedLiterals;
  protected int scanRequest;

  OptimizingSearchHelperBase() {
    scanRequest = 0;
    scanned = new THashSet<>();
    scannedText = new THashSet<>();
    scannedComments = new THashSet<>();
    scannedLiterals = new THashSet<>();
  }

  @Override
  public void clear() {
    scanned.clear();
    scannedText.clear();
    scannedComments.clear();
    scannedLiterals.clear();
  }

  @Override
  public void addWordToSearchInCode(String word) {
    if (word != null && doOptimizing() && scanned.add(word)) {
      doAddSearchWordInCode(word);
    }
  }

  @Override
  public void addWordToSearchInText(String word) {
    if (word != null && doOptimizing() && scannedText.add(word)) {
      doAddSearchWordInText(word);
    }
  }

  @Override
  public void addWordToSearchInComments(String word) {
    if (word != null && doOptimizing() && scannedComments.add(word)) {
      doAddSearchWordInComments(word);
    }
  }

  @Override
  public void addWordToSearchInLiterals(String word) {
    if (word != null && doOptimizing() && scannedLiterals.add(word)) {
      doAddSearchWordInLiterals(word);
    }
  }

  protected abstract void doAddSearchWordInCode(@NotNull String word);
  protected abstract void doAddSearchWordInText(@NotNull String word);
  protected abstract void doAddSearchWordInComments(@NotNull String word);
  protected abstract void doAddSearchWordInLiterals(@NotNull String word);

  @Override
  public void endTransaction() {
    scanRequest++;
  }

  @Override
  public boolean isScannedSomething() {
    return !scanned.isEmpty() || !scannedText.isEmpty() || !scannedComments.isEmpty() || !scannedLiterals.isEmpty();
  }
}
