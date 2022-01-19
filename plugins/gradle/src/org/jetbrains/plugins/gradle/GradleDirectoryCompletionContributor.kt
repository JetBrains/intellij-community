// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle

import com.intellij.ide.actions.CreateDirectoryCompletionContributor
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDirectoryCompletionContributor : CreateDirectoryCompletionContributor {
  override fun getDescription(): String = GradleBundle.message("gradle.tasks.directory.completion.description");

  override fun getVariants(directory: PsiDirectory): Collection<CreateDirectoryCompletionContributor.Variant> {
    val project = directory.project

    val module = ProjectFileIndex.getInstance(project).getModuleForFile(directory.virtualFile) ?: return emptyList()

    val moduleProperties = ExternalSystemModulePropertyManager.getInstance(module)
    if (GradleConstants.SYSTEM_ID.id != moduleProperties.getExternalSystemId()) return emptyList()

    val result = mutableListOf<CreateDirectoryCompletionContributor.Variant>()
    fun addAll(data: ContentRootData, type: ExternalSystemSourceType, rootType: JpsModuleSourceRootType<*>) {
      data.getPaths(type).mapTo(result) { CreateDirectoryCompletionContributor.Variant(it.path, rootType) }
    }

    GradleContentRootContributor.processContentRoots(module) { rootData ->
      addAll(rootData, ExternalSystemSourceType.SOURCE, JavaSourceRootType.SOURCE)
      addAll(rootData, ExternalSystemSourceType.TEST, JavaSourceRootType.TEST_SOURCE)
      addAll(rootData, ExternalSystemSourceType.RESOURCE, JavaResourceRootType.RESOURCE)
      addAll(rootData, ExternalSystemSourceType.TEST_RESOURCE, JavaResourceRootType.TEST_RESOURCE)
    }

    return result.toList()
  }
}
