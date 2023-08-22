// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
public interface OptimizingSearchHelper {
  boolean doOptimizing();
  void clear();

  void addWordToSearchInCode(@NotNull String word);

  void addWordToSearchInText(@NotNull String word);

  void addWordToSearchInComments(@NotNull String word);

  void addWordToSearchInLiterals(@NotNull String word);

  void endTransaction();

  boolean isScannedSomething();

  @NotNull
  Set<VirtualFile> getFilesSetToScan();
}
