// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.ui

import com.intellij.openapi.project.Project
import com.intellij.refactoring.ui.MethodSignatureComponent
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinSignatureComponent(
    signature: String, project: Project
) : MethodSignatureComponent(signature, project, KotlinFileType.INSTANCE) {
    private val myFileName = "dummy." + KotlinFileType.EXTENSION

    override fun getFileName(): String = myFileName
}
