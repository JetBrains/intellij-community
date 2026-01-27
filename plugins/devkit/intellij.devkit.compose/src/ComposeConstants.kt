// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

internal const val COMPOSE_MAVEN = "org.jetbrains.compose.foundation:foundation-desktop"
internal const val INTELLIJ_LIBS_COMPOSE_MAVEN = "bundledModule:intellij.libraries.compose.foundation.desktop"

// in JPS *.iml definition
internal const val COMPOSE_HOT_RELOAD_ENABLED_MARKER = "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"

internal fun hasCompose(m: Module?) :Boolean {
  return JavaLibraryUtil.hasLibraryJar(m, COMPOSE_MAVEN)
         || JavaLibraryUtil.hasLibraryJar(m, INTELLIJ_LIBS_COMPOSE_MAVEN)
}

internal fun hasCompose(p: Project) :Boolean {
  return JavaLibraryUtil.hasLibraryJar(p, COMPOSE_MAVEN)
         || JavaLibraryUtil.hasLibraryJar(p, INTELLIJ_LIBS_COMPOSE_MAVEN)
}