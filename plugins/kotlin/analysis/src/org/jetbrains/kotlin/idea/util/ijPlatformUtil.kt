// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("IjPlatformUtil")

package org.jetbrains.kotlin.idea.util


import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.kotlin.idea.util.application.runReadAction

fun getProjectJdkTableSafe(): ProjectJdkTable = runReadAction { ProjectJdkTable.getInstance() }
