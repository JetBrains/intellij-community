// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.psi.PsiElement

class MetaModelBuilderException(errorMessage: String, val psiToHighlight: PsiElement?) : RuntimeException(errorMessage)

class WorkspaceEntityInheritsEntitySourceException(entityFqn: String) :
  IllegalStateException("$entityFqn extends WorkspaceEntity and EntitySource at the same time, which is prohibited.")

class WorkspaceEntityMultipleInheritanceException(entityFqn: String, supers: Set<String>) :
  IllegalStateException("$entityFqn extends multiple @Abstract entities, which is prohibited: ${supers.joinToString(", ")}.")
