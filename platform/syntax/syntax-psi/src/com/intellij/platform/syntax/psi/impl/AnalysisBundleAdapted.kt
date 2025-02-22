// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.analysis.AnalysisBundle
import com.intellij.platform.syntax.util.BundleAdapter

object AnalysisBundleAdapted : BundleAdapter {
  override fun message(key: String, vararg params: Any?): String {
    return AnalysisBundle.message(key, *params)
  }
}