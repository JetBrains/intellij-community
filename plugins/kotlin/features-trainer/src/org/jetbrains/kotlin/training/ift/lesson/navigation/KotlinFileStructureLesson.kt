// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ift.lesson.navigation

import com.intellij.openapi.editor.LogicalPosition
import training.learn.lesson.general.navigation.FileStructureLesson

class KotlinFileStructureLesson : FileStructureLesson() {
    override val sampleFilePath: String = "src/FileStructureDemo.kt"
    override val methodToFindPosition: LogicalPosition = LogicalPosition(73, 8)
}