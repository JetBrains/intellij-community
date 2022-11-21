// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.kotlin.utils.Printer

class WorkspaceModelPrinter(
    private val moduleContributor: WorkspaceModelPrinterContributor<ModulePrinterEntity>? = null,
) {
    private val printer = Printer(StringBuilder())

    fun print(project: Project): String {
        processModules(project)

        return printer.toString()
    }

    private fun processModules(project: Project) = processEntities(
        title = "MODULES",
        contributor = moduleContributor,
        entities = runReadAction { ModuleManager.getInstance(project).modules }.map(Module::toPrinterEntity),
    )

    private fun <EntityType : ContributableEntity> processEntities(
        title: String,
        contributor: WorkspaceModelPrinterContributor<EntityType>?,
        entities: List<EntityType>,
    ) {
        if (contributor == null) return

        printer.println(title)
        printer.indented {
            for (entity in entities.sortedBy { it.presentableName }) {
                printer.println(entity.presentableName)
                contributor.process(entity, printer)
            }
        }
    }
}

internal fun Printer.indented(block: () -> Unit) {
    pushIndent()
    block()
    popIndent()
}
