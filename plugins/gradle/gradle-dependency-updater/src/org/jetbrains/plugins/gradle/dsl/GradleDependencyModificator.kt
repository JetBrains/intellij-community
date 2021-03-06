// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.repositories.UrlBasedRepositoryModelImpl
import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.ExternalDependencyModificator
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

class GradleDependencyModificator(val myProject: Project) : ExternalDependencyModificator {
  override fun supports(module: Module): Boolean {
    return ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId() == SYSTEM_ID.id
  }

  override fun addDependency(module: Module, descriptor: UnifiedDependency) {
    checkDescriptor(descriptor);
    val artifactSpec = ArtifactDependencySpec.create(descriptor.coordinates.artifactId!!, descriptor.coordinates.groupId,
                                                     descriptor.coordinates.version)
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    val configurationName = getConfigurationName(descriptor)
    val dependencies: DependenciesModel = model.dependencies()
    dependencies.addArtifact(configurationName, artifactSpec)
    applyChanges(model)
  }

  private fun checkDescriptor(descriptor: UnifiedDependency) {
    requireNotNull(descriptor.coordinates.artifactId){GradleBundle.message("gradle.dsl.artifactid.is.null")}
    requireNotNull(descriptor.coordinates.groupId){GradleBundle.message("gradle.dsl.groupid.is.null")}
    requireNotNull(descriptor.coordinates.version){GradleBundle.message("gradle.dsl.version.is.null")}
  }

  private fun getConfigurationName(descriptor: UnifiedDependency): String {
    return descriptor.scope ?: "implementation"
  }

  override fun updateDependency(module: Module,
                                oldDescriptor: UnifiedDependency,
                                newDescriptor: UnifiedDependency) {
    checkDescriptor(newDescriptor);
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    val dependencies: DependenciesModel = model.dependencies()
    for (artifactModel in dependencies.artifacts()) {
      if (artifactModel.group().valueAsString() == oldDescriptor.coordinates.groupId
          && artifactModel.name().valueAsString() == oldDescriptor.coordinates.artifactId
          && artifactModel.version().valueAsString() == oldDescriptor.coordinates.version
      ) {
        updateVariableOrValue(artifactModel.group(), newDescriptor.coordinates.groupId!!)
        updateVariableOrValue(artifactModel.name(), newDescriptor.coordinates.artifactId!!)
        updateVariableOrValue(artifactModel.version(), newDescriptor.coordinates.version!!)
      }
    }
    return applyChanges(model)
  }

  private fun updateVariableOrValue(model: ResolvedPropertyModel, value: String) {
    if (model.dependencies.size == 1) {
      val dependencyPropertyModel = model.dependencies[0]
      if (dependencyPropertyModel.propertyType == PropertyType.VARIABLE ||
          dependencyPropertyModel.propertyType == PropertyType.REGULAR ||
          dependencyPropertyModel.propertyType == PropertyType.PROPERTIES_FILE
      ) {
        if (dependencyPropertyModel.valueAsString().equals(model.valueAsString())) { // not partial injection, like "${version}-SNAPSHOT"
          dependencyPropertyModel.setValue(value);
          return
        }
      }
    }
    model.setValue(value)
  }

  override fun removeDependency(module: Module, descriptor: UnifiedDependency) {
    checkDescriptor(descriptor);
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    val dependencies: DependenciesModel = model.dependencies()
    for (artifactModel in dependencies.artifacts()) {
      if (artifactModel.group().valueAsString() == descriptor.coordinates.groupId
          && artifactModel.name().valueAsString() == descriptor.coordinates.artifactId
          && artifactModel.version().valueAsString() == descriptor.coordinates.version
      ) {
        dependencies.remove(artifactModel)
        break
      }
    }
    return applyChanges(model)
  }

  override fun addRepository(module: Module, repository: UnifiedDependencyRepository) {
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    val repositoryModel = model.repositories()
    val trimmedUrl = repository.url.trimLastSlash()
    if (repositoryModel.containsMavenRepositoryByUrl(trimmedUrl)) {
      return
    }

    val methodName = methodsByRepo[trimmedUrl]
    if (methodName != null) {
      if (repositoryModel.containsMethodCall(methodName)) {
        return
      }
      repositoryModel.addRepositoryByMethodName(methodName)
    }
    else {
      repositoryModel.addMavenRepositoryByUrl(repository.url, repository.name)
    }
    applyChanges(model)
  }

  override fun deleteRepository(module: Module, repository: UnifiedDependencyRepository) {
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    val repositoryModel = model.repositories()
    repositoryModel.removeRepositoryByUrl(repository.url)
    repositoryModel.removeRepositoryByUrl(repository.url.trimLastSlash())
    applyChanges(model)
  }

  override fun declaredDependencies(module: @NotNull Module): List<DeclaredDependency> {
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    return model.dependencies().artifacts().map {
      val dataContext = object: DataContext {
        override fun getData(dataId: String): Any? {
          if(CommonDataKeys.PSI_ELEMENT.`is`(dataId)){
            return it.psiElement
          }
          return null
        }
      }
      DeclaredDependency(it.group().valueAsString(), it.name().valueAsString(), it.version().valueAsString(), it.configurationName(),
                         dataContext)
    }
  }

  override fun declaredRepositories(module: Module): List<UnifiedDependencyRepository> {
    val model = ProjectBuildModel.get(module.project).getModuleBuildModel(module) ?: throwFailToModify(module);
    return model.repositories().repositories()
      .mapNotNull { it as? UrlBasedRepositoryModelImpl }
      .mapNotNull { m ->
        m.url().valueAsString()?.let {
          UnifiedDependencyRepository(m.name().valueAsString(), m.name().valueAsString(), it)
        }
      }
  }

  private fun throwFailToModify(module: Module): Nothing {
    throw IllegalStateException(GradleBundle.message("gradle.dsl.model.fail.to.build", module.name))
  }

  private fun applyChanges(model: @Nullable GradleBuildModel) {
    WriteCommandAction.writeCommandAction(myProject, model.psiFile).run<Throwable> {
      model.applyChanges()
    }
  }

  companion object {
    val reposByMethod = mapOf("mavenCentral" to "https://repo1.maven.org/maven2", "jcenter" to "https://jcenter.bintray.com")
    val methodsByRepo = reposByMethod.map { it.value to it.key }.toMap()
  }

  private fun String.trimLastSlash(): String {
    return this.trimEnd { it == '/' }
  }

}