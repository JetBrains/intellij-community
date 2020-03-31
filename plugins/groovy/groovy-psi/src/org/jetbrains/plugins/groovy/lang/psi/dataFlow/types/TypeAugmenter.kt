// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable

abstract class TypeAugmenter {

  companion object {
    private val EP_NAME: ExtensionPointName<TypeAugmenter> = ExtensionPointName.create("org.intellij.groovy.typeAugmenter")

    fun inferAugmentedType(variable: GrVariable): PsiType? {
      for (augmenter in EP_NAME.extensions) {
        augmenter.inferType(variable)?.run {
          return this
        }
      }
      return null
    }

  }

  abstract fun inferType(variable: GrVariable): PsiType?

}