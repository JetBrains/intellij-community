// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Modified part of the text
 *
 * Offset ranges cover whole line, including '\n' at the end. But '\n' can be absent for the last line.
 */
public interface LineFragment extends DiffFragment {
  int getStartLine1();

  int getEndLine1();

  int getStartLine2();

  int getEndLine2();

  /**
   * High-granularity changes inside line fragment (ex: detected by ByWord)
   * Offsets of inner changes are relative to the start of LineFragment.
   *
   * null - no inner similarities was found
   */
  @Nullable
  List<DiffFragment> getInnerFragments();
}
