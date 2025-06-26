// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.psi.PsiElement
import com.intellij.workspaceModel.codegen.deft.meta.ValueType

class IncorrectObjInterfaceException(errorMessage: String) : RuntimeException(errorMessage)

class WorkspaceEntityInheritsEntitySourceException(entityFqn: String) :
  IllegalStateException("$entityFqn extends WorkspaceEntity and EntitySource at the same time, which is prohibited.")

class WorkspaceEntityMultipleInheritanceException(entityFqn: String, supers: Set<String>) :
  IllegalStateException("$entityFqn extends multiple @Abstract entities, which is prohibited: ${supers.joinToString(", ")}.")

interface ObjMetaElementWithPsi {
  val sourcePsi: PsiElement?
}

fun unsupportedType(type: String?): ValueType<*> {
  throw IncorrectObjInterfaceException("Unsupported type '$type'")
}
