// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

object HtmlColorClasses : ReportColors<String> {
  override val perfectSortingColor = "perfect"
  override val goodSortingColor = "good"
  override val badSortingColor = "bad"
  override val notFoundColor = "not-found"
  override val absentLookupColor = "absent"
  override val goodSortingThreshold = 5
}