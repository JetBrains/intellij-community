// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments

import com.intellij.diff.util.ThreeSide

interface MergeLineFragment {
  fun getStartLine(side: ThreeSide): Int

  fun getEndLine(side: ThreeSide): Int
}
