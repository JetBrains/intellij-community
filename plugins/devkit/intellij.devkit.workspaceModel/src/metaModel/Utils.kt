// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.psi.PsiElement
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

interface ObjMetaElementWithPsi {
  val sourcePsi: PsiElement?
}

fun entityMetaError(message: String, psiToHighlight: PsiElement?): ValueType<*> {
  throw MetaModelBuilderException(message, psiToHighlight)
}

fun unsupportedType(type: String?): ValueType<*> {
  throw InternalMetaModelBuilderException("Unsupported type '$type'")
}

class MetaProblem(val message: String, val psiToHighlight: PsiElement?)

internal data class CompiledObjModuleAndK2Module(val compiledObjModule: CompiledObjModule, val kotlinModule: KaModule)

internal infix fun CompiledObjModule.and(kaModule: KaModule) = CompiledObjModuleAndK2Module(this, kaModule)
