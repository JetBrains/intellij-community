// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.utils

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import java.io.File

fun findInspectionFile(startDir: File, mode: KotlinPluginMode): File? {
    return findInspectionFile(startDir, if (mode == KotlinPluginMode.K1) ".inspection" else ".k2Inspection")
}

fun findInspectionFile(startDir: File, inspectionFileName: String): File? {
    var currentDir: File? = startDir
    while (currentDir != null) {
        val inspectionFile = File(currentDir, inspectionFileName)
        if (inspectionFile.exists()) {
            return inspectionFile
        }
        currentDir = currentDir.parentFile
    }
    return null
}