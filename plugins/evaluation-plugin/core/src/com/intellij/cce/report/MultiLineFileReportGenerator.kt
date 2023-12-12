// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.workspace.storages.FeaturesStorage

class MultiLineFileReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs) {

  override val spanClass = "multiline ${super.spanClass}"

  override fun textToInsert(session: Session): String = session.expectedText.lines().first()
}
