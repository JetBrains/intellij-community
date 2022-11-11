// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

class WorkspaceModelPrinterFactory {
    private val myModulePrinterContributors = mutableListOf<ModulePrinterContributor>()
    private val myLibraryPrinterContributors = mutableListOf<LibraryPrinterContributor>()
    private val mySdkPrinterContributors = mutableListOf<SdkPrinterContributor>()

    fun addContributor(contributor: WorkspaceModelPrinterContributor<*>) {
        when (contributor) {
            is ModulePrinterContributor -> myModulePrinterContributors.add(contributor)
            is LibraryPrinterContributor -> myLibraryPrinterContributors.add(contributor)
            is SdkPrinterContributor -> mySdkPrinterContributors.add(contributor)
            else -> error("Unexpected contributor: ${contributor::class.qualifiedName}")
        }
    }

    fun build(): WorkspaceModelPrinter {
        check(myModulePrinterContributors.isNotEmpty() || myLibraryPrinterContributors.isNotEmpty() || mySdkPrinterContributors.isNotEmpty()) {
            "Workspace model printer should have at least one contributor"
        }

        return WorkspaceModelPrinter(
            compositeContributor(*myModulePrinterContributors.toTypedArray()),
            compositeContributor(*myLibraryPrinterContributors.toTypedArray()),
            compositeContributor(*mySdkPrinterContributors.toTypedArray()),
        )
    }
}

@Suppress("TestFunctionName")
fun WorkspaceModelPrinter(
    printerConfiguration: WorkspaceModelPrinterFactory.() -> Unit
): WorkspaceModelPrinter {
    return WorkspaceModelPrinterFactory().also(printerConfiguration).build()
}
