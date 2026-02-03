// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.projectWizard.generators.AssetsJava
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep

private const val DEFAULT_FILE_NAME = "Main.groovy"
private const val DEFAULT_TEMPLATE_NAME = "Groovy Sample Code"

fun AssetsNewProjectWizardStep.withGroovySampleCode(
  sourceRootPath: String,
  packageName: String? = null,
  fileName: String = DEFAULT_FILE_NAME,
  templateName: String = DEFAULT_TEMPLATE_NAME,
) {
  val sourcePath = AssetsJava.getJavaSampleSourcePath(sourceRootPath, packageName, fileName)
  addTemplateAsset(sourcePath, templateName, buildMap {
    if (packageName != null) {
      put("PACKAGE_NAME", packageName)
    }
  })
  addFilesToOpen(sourcePath)
}