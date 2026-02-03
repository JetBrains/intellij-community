// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import com.intellij.diff.util.ThreeSide

interface MergeWordFragment {
  fun getStartOffset(side: ThreeSide): Int

  fun getEndOffset(side: ThreeSide): Int
}
