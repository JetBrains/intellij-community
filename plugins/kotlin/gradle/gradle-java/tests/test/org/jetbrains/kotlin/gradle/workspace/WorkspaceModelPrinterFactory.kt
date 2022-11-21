// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

class WorkspaceModelPrinterFactory {
    private val myModulePrinterContributors = mutableListOf<ModulePrinterContributor>()

    fun addContributor(contributor: ModulePrinterContributor) {
        myModulePrinterContributors.add(contributor)
    }

    fun build(): WorkspaceModelPrinter {
        check(myModulePrinterContributors.isNotEmpty()) {
            "Workspace model printer should have at least one contributor"
        }

        return WorkspaceModelPrinter(
            myModulePrinterContributors
        )
    }
}

@Suppress("TestFunctionName")
fun WorkspaceModelPrinter(
    printerConfiguration: WorkspaceModelPrinterFactory.() -> Unit
): WorkspaceModelPrinter {
    return WorkspaceModelPrinterFactory().also(printerConfiguration).build()
}

@Suppress("TestFunctionName")
fun WorkspaceModelPrinterFactory(
    printerConfiguration: WorkspaceModelPrinterFactory.() -> Unit
): WorkspaceModelPrinterFactory {
    return WorkspaceModelPrinterFactory().also(printerConfiguration)
}
