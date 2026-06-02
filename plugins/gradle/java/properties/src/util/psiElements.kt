// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.properties.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val PsiElement.module: Module?
  get() = containingFile?.originalFile?.virtualFile?.let { ProjectFileIndex.getInstance(project).getModuleForFile(it) }