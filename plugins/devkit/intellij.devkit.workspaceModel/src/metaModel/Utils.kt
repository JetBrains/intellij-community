// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.psi.PsiElement
import com.intellij.workspaceModel.codegen.deft.meta.ValueType

class IncorrectObjInterfaceException(errorMessage: String) : RuntimeException(errorMessage)

interface ObjMetaElementWithPsi {
  val sourcePsi: PsiElement?
}

fun unsupportedType(type: String?): ValueType<*> {
  throw IncorrectObjInterfaceException("Unsupported type '$type'")
}
