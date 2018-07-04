// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
public interface OptimizingSearchHelper {
  boolean doOptimizing();
  void clear();

  void addWordToSearchInCode(String word);

  void addWordToSearchInText(String word);

  void addWordToSearchInComments(String word);

  void addWordToSearchInLiterals(String word);

  void endTransaction();

  boolean isScannedSomething();

  @NotNull
  Set<PsiFile> getFilesSetToScan();
}
