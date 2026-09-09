// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.openapi.application.readAction
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Computes the footer info of a search result: the path on the left, and the action offered on the right.
 */
@ApiStatus.Experimental
interface SeExtendedInfoCalculator {
  /**
   * Returns the footer info of [item].
   */
  suspend fun infoFor(item: SeTargetRawItem): SeExtendedInfo
}

/**
 * The [SeExtendedInfoCalculator] of a goto model. It shows the path of the file, and it offers the
 * action that opens the file in a right split.
 */
@ApiStatus.Internal
class SePsiExtendedInfoCalculator : SeExtendedInfoCalculator {
  private val extendedInfo: ExtendedInfo by lazy(LazyThreadSafetyMode.PUBLICATION) {
    createPsiExtendedInfo(psiElement = { PSIPresentationBgRendererWrapper.toPsi(it) })
  }

  override suspend fun infoFor(item: SeTargetRawItem): SeExtendedInfo =
    readAction { SeExtendedInfoBuilder().withExtendedInfo(extendedInfo, item.rawObject).build() }
}
