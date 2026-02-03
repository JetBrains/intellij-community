// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.psi.PsiElement
import com.intellij.workspaceModel.codegen.deft.meta.ValueType

interface ObjMetaElementWithPsi {
  val sourcePsi: PsiElement?
}

fun unsupportedType(type: String?, psiToHighlight: PsiElement? = null): ValueType<*> {
  throw MetaModelBuilderException("Unsupported type '$type'", psiToHighlight)
}

class MetaProblem(val message: String, val psiToHighlight: PsiElement?)
