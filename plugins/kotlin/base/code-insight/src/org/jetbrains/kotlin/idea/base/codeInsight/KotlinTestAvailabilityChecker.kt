// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.name.ClassId

@Service(Service.Level.PROJECT)
class KotlinTestAvailabilityChecker(project: Project) : FrameworkAvailabilityChecker(project) {
    companion object {
        val TEST_FQ_NAME = ClassId.fromString("kotlin/test/Test")
        val IGNORE_FQ_NAME = ClassId.fromString("kotlin/test/Ignore")
    }
    
    override val fqNames = setOf(TEST_FQ_NAME.asFqNameString())

    override val javaClassLookup = true
    override val aliasLookup = true // `kotlin.test.Test` might be a typealias
    override val kotlinFullClassLookup = true // `kotlin.test.Test` might be an expected/actual annotation class
}