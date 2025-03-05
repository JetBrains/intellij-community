// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class InitDescriptorDocumentationsDownloadActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    DocumentationContentProvider.getInstance().initializeContentDownload()
  }
}
