// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.metaModel

import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

internal data class CompiledObjModuleAndK2Module(val compiledObjModule: CompiledObjModule, val kotlinModule: KaModule)

internal infix fun CompiledObjModule.and(kaModule: KaModule) = CompiledObjModuleAndK2Module(this, kaModule)
