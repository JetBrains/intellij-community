// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider

/**
 * @author Vladislav.Soroka
 */
class GradleDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    if (expression !is GrClosableBlock) return null
    val resultFromAction = GradleActionDelegatesToProvider().getDelegatesToInfo(expression)
    if (resultFromAction != null) {
      return resultFromAction
    }
    val file = expression.containingFile
    if (file == null || !FileUtilRt.extensionEquals(file.name, GradleConstants.EXTENSION)) return null

    for (contributor in GradleMethodContextContributor.EP_NAME.extensions) {
      val delegatesToInfo = contributor.getDelegatesToInfo(expression)
      if (delegatesToInfo != null) {
        return delegatesToInfo
      }
    }
    return null
  }
}
