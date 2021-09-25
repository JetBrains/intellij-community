// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve.util

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

// ideally we should do something if dumbService.isAlternativeResolveEnabled, because platform expects resolve to work in such cases
fun Project.isInDumbMode(): Boolean = DumbService.getInstance(this).isDumb

