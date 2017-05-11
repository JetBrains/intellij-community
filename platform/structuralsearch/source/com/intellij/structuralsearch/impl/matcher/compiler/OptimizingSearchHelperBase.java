/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.impl.matcher.compiler;

import gnu.trove.THashSet;

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
    scannedComments.clear();
    scannedLiterals.clear();
  }

  @Override
  public boolean addWordToSearchInCode(final String refname) {
    if (doOptimizing() && scanned.add(refname)) {
      doAddSearchWordInCode(refname);
      return true;
    }
    return false;
  }

  @Override
  public boolean addWordToSearchInText(final String refname) {
    if (doOptimizing() && scannedText.add(refname)) {
      doAddSearchWordInText(refname);
      return true;
    }
    return false;
  }

  @Override
  public boolean addWordToSearchInComments(final String refname) {
    if (doOptimizing() && scannedComments.add(refname)) {
      doAddSearchWordInComments(refname);
      return true;
    }
    return false;
  }

  @Override
  public boolean addWordToSearchInLiterals(final String refname) {
    if (doOptimizing() && scannedLiterals.add(refname)) {
      doAddSearchWordInLiterals(refname);
      return true;
    }
    return false;
  }

  protected abstract void doAddSearchWordInCode(final String refname);
  protected abstract void doAddSearchWordInText(final String refname);

  protected abstract void doAddSearchWordInComments(final String refname);
  protected abstract void doAddSearchWordInLiterals(final String refname);

  @Override
  public void endTransaction() {
    scanRequest++;
  }

  @Override
  public boolean isScannedSomething() {
    return scanned.size() > 0 || scannedComments.size() > 0 || scannedLiterals.size() > 0;
  }
}
