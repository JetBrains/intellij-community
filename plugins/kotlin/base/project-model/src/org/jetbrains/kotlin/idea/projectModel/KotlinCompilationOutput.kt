// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File
import java.io.Serializable

interface KotlinCompilationOutput : Serializable {
    val classesDirs: Set<File>
    val effectiveClassesDir: File?
    val resourcesDir: File?
}