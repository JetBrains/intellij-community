// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable

/**
 * This method is equivalent to {@sample invalidateProjectRoots(RootsChangeRescanningInfo.TOTAL_RESCAN)}
 * Consider using optimised instance of  [com.intellij.util.indexing.BuildableRootsChangeRescanningInfo]
 */
fun Project.invalidateProjectRoots() {
    ProjectRootManagerEx.getInstanceEx(this).makeRootsChange(EmptyRunnable.INSTANCE, false, true)
}

fun Project.invalidateProjectRoots(info: RootsChangeRescanningInfo) {
    ProjectRootManagerEx.getInstanceEx(this).makeRootsChange(EmptyRunnable.INSTANCE, info)
}