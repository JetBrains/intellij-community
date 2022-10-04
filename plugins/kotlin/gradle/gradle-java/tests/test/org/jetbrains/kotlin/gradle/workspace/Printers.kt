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

object WorkspaceModelPrinters {
    val moduleNamesPrinter: WorkspaceModelPrinter
        get() = ProjectPrinter {
            addContributor(NoopModulePrinterContributor())
        }

    val moduleDependenciesPrinter get() = ProjectPrinter {
        addContributor(SanitizingOrderEntryPrinterContributor())
    }

    val moduleKotlinFacetSettingsPrinter get() = ProjectPrinter {
        addContributor(KotlinFacetSettingsPrinterContributor())
    }

    val libraryNamesPrinter get() = ProjectPrinter {
        addContributor(SanitizingLibraryPrinterContributor())
    }

    val sdkNamesPrinter get() = ProjectPrinter {
        addContributor(NoopSdkPrinterContributor())
    }

    val fullWorkspacePrinter get() = ProjectPrinter {
        addContributor(KotlinFacetSettingsPrinterContributor())
        addContributor(SanitizingOrderEntryPrinterContributor())
        addContributor(SanitizingLibraryPrinterContributor())
        addContributor(NoopSdkPrinterContributor())
    }
}

class WorkspaceModelPrinter(
    private val moduleContributor: WorkspaceModelPrinterContributor<ModulePrinterEntity>? = null,
    private val libraryContributor: WorkspaceModelPrinterContributor<LibraryPrinterEntity>? = null,
    private val sdkContributor: WorkspaceModelPrinterContributor<SdkPrinterEntity>? = null,
) {
    private val printer = Printer(StringBuilder())

    fun print(project: Project): String {
        processModules(project)
        processLibraries(project)
        processSdks()

        return printer.toString()
    }

    private fun processModules(project: Project) = processEntities(
        title = "MODULES",
        contributor = moduleContributor,
        entities = runReadAction { ModuleManager.getInstance(project).modules }.map(Module::toPrinterEntity),
    )

    private fun processLibraries(project: Project) = processEntities(
        title = "LIBRARIES",
        contributor = libraryContributor,
        entities = runReadAction { LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries }.map(Library::toPrinterEntity),
    )

    private fun processSdks() = processEntities(
        title = "SDK",
        contributor = sdkContributor,
        entities = runReadAction { ProjectJdkTable.getInstance().allJdks }.map(Sdk::toPrinterEntity),
    )

    private fun <EntityType : ContributableEntity> processEntities(
        title: String,
        contributor: WorkspaceModelPrinterContributor<EntityType>?,
        entities: List<EntityType>,
    ) {
        if (contributor == null) return

        val preprocessedEntities = contributor.preprocess(entities)
        if (preprocessedEntities.isEmpty()) return

        printer.println(title)
        printer.indented {
            for (entity in preprocessedEntities.sortedBy { it.presentableName }) {
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
