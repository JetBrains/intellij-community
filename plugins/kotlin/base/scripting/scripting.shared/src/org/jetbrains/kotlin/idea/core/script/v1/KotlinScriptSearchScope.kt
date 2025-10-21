// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.project.Project
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope

class KotlinScriptSearchScope(project: Project, baseScope: GlobalSearchScope) : DelegatingGlobalSearchScope(project, baseScope)