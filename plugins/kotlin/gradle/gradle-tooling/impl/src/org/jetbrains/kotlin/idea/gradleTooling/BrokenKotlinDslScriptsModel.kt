// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import java.io.File
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter

class BrokenKotlinDslScriptsModel(exception: Throwable) : KotlinDslScriptsModel, Serializable {
    val message = exception.message
    val stackTrace = StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString()

    override fun getScriptModels(): Map<File, KotlinDslScriptModel> = mapOf()
}