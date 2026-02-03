// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
abstract class OptimizingSearchHelperBase implements OptimizingSearchHelper {
  private final Set<String> scanned = new HashSet<>();
  private final Set<String> scannedText = new HashSet<>();
  private final Set<String> scannedComments = new HashSet<>();
  private final Set<String> scannedLiterals = new HashSet<>();
  protected int scanRequest;

  @Override
  public void clear() {
    scanned.clear();
    scannedText.clear();
    scannedComments.clear();
    scannedLiterals.clear();
  }

  @Override
  public void addWordToSearchInCode(@NotNull String word) {
    if (doOptimizing() && scanned.add(word)) {
      doAddSearchWordInCode(word);
    }
  }

  @Override
  public void addWordToSearchInText(@NotNull String word) {
    if (doOptimizing() && scannedText.add(word)) {
      doAddSearchWordInText(word);
    }
  }

  @Override
  public void addWordToSearchInComments(@NotNull String word) {
    if (doOptimizing() && scannedComments.add(word)) {
      doAddSearchWordInComments(word);
    }
  }

  @Override
  public void addWordToSearchInLiterals(@NotNull String word) {
    if (doOptimizing() && scannedLiterals.add(word)) {
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
