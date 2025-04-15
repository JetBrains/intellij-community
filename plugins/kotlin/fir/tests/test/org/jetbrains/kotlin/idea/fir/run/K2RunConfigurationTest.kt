// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.run

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.idea.run.AbstractRunConfigurationTest
import org.jetbrains.kotlin.idea.run.getJavaRunParameters
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class K2RunConfigurationTest : AbstractRunConfigurationTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testMoveObjectWithMain() {
        val oldPackageName = "foo"
        val objectName = "MainObject"
        val newPackageName = "bar"
        val expectedFqNameBefore = "$oldPackageName.$objectName"
        val expectedFqNameAfter = "$newPackageName.$objectName"

        configureProject()
        val configuredModule = defaultConfiguredModule
        val kotlinRunConfiguration = createConfigurationFromMain(project, "$expectedFqNameBefore.main", save = true)

        val javaParametersBefore = getJavaRunParameters(kotlinRunConfiguration)
        val fqNameBefore = javaParametersBefore.mainClass
        assert(fqNameBefore == expectedFqNameBefore) {
            "The main class in the run configuration before the move should've been '$expectedFqNameBefore', but it was: '$fqNameBefore'"
        }

        val mainFunction = findMainFunction(project, javaParametersBefore.mainClass + ".main")
        val mainObject = mainFunction.containingClassOrObject as? KtObjectDeclaration
        check(mainObject != null) { "Main object not found" }
        val targetFileName = mainObject.containingKtFile.name

        val moveDescriptor = K2MoveOperationDescriptor.Declarations(
            project = project,
            declarations = listOf(mainObject),
            baseDir = configuredModule.srcDir?.toPsiDirectory(project) ?: error("Module source directory not found"),
            fileName = targetFileName,
            pkgName = FqName(newPackageName),
            searchForText = false,
            searchReferences = true,
            searchInComments = false,
            mppDeclarations = false,
            dirStructureMatchesPkg = true
        )

        K2MoveDeclarationsRefactoringProcessor(moveDescriptor).run()

        assert(configuredModule.srcDir?.findFileByRelativePath("$newPackageName/$targetFileName") != null) {
            "Move was not successful"
        }

        val mainClassAfter = getJavaRunParameters(kotlinRunConfiguration).mainClass
        assert(mainClassAfter == expectedFqNameAfter) {
            "The main class in the run configuration after the move should've been '$expectedFqNameAfter', but it was: '$mainClassAfter'"
        }
    }

    fun testMoveTopLevelMainFunction() {
        val oldPackageName = "foo"
        val facadeName = "MainFunctionKt"
        val newPackageName = "bar"
        val expectedFqNameBefore = "$oldPackageName.$facadeName"
        val expectedFqNameAfter = "$newPackageName.$facadeName"

        configureProject()
        val configuredModule = defaultConfiguredModule
        val kotlinRunConfiguration = createConfigurationFromMain(project, "$oldPackageName.main", save = true)

        val javaParametersBefore = getJavaRunParameters(kotlinRunConfiguration)
        val fqNameBefore = javaParametersBefore.mainClass
        assert(fqNameBefore == expectedFqNameBefore) {
            "The main class in the run configuration before the move should've been '$expectedFqNameBefore', but it was: '$fqNameBefore'"
        }

        val mainFunction = findMainFunction(project,  "$oldPackageName.main")
        val targetFileName = mainFunction.containingFile.name

        val moveDescriptor = K2MoveOperationDescriptor.Declarations(
            project = project,
            declarations = listOf(mainFunction),
            baseDir = configuredModule.srcDir?.toPsiDirectory(project) ?: error("Module source directory not found"),
            fileName = targetFileName,
            pkgName = FqName(newPackageName),
            searchForText = false,
            searchReferences = true,
            searchInComments = false,
            mppDeclarations = false,
            dirStructureMatchesPkg = true
        )

        K2MoveDeclarationsRefactoringProcessor(moveDescriptor).run()

        assert(configuredModule.srcDir?.findFileByRelativePath("$newPackageName/$targetFileName") != null) {
            "Move was not successful"
        }

        val mainClassAfter = getJavaRunParameters(kotlinRunConfiguration).mainClass
        assert(mainClassAfter == expectedFqNameAfter) {
            "The main class in the run configuration after the move should've been '$expectedFqNameAfter', but it was: '$mainClassAfter'"
        }
    }
}
