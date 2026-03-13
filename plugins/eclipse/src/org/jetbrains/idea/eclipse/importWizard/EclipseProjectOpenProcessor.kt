// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.idea.eclipse.importWizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectOpenProcessorBase
import org.jetbrains.idea.eclipse.EclipseProjectFinder
import org.jetbrains.idea.eclipse.EclipseXml
import kotlin.io.path.Path
import kotlin.io.path.name

internal class EclipseProjectOpenProcessor : ProjectOpenProcessorBase<EclipseImportBuilder>() {
  override fun doGetBuilder(): EclipseImportBuilder {
    return ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(EclipseImportBuilder::class.java)
  }

  override val supportedExtensions: Array<String>
    get() = arrayOf(EclipseXml.CLASSPATH_FILE, EclipseXml.PROJECT_FILE)

  public override fun doQuickImport(file: VirtualFile, wizardContext: WizardContext): Boolean {
    val rootDirectory = file.parent.path
    builder.setRootDirectory(rootDirectory)
    val projects = builder.list
    if (projects.isNullOrEmpty() || !projects.contains(rootDirectory)) {
      return false
    }

    builder.list = projects
    wizardContext.projectName = EclipseProjectFinder.findProjectName(rootDirectory) ?: Path(rootDirectory).name
    return true
  }
}
