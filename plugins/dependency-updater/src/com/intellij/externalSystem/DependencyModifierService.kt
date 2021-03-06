package com.intellij.externalSystem

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
class DependencyModifierService(private val myProject: Project) {

  fun addRepository(module: Module, repository: UnifiedDependencyRepository) = modify(module) {
    it.addRepository(module, repository)
  }

  fun deleteRepository(module: Module, repository: UnifiedDependencyRepository) = modify(module) {
    it.deleteRepository(module, repository)
  }

  fun declaredDependencies(module: Module): List<DeclaredDependency> = read(module) {
    it.declaredDependencies(module);
  }

  fun declaredRepositories(module: Module): List<UnifiedDependencyRepository> = read(module)  {
    it.declaredRepositories(module);
  }

  fun supports(module: Module): Boolean {
    return ExternalDependencyModificator.EP_NAME.getExtensionList(myProject).any { it.supports(module) }
  }

  fun addDependency(module: Module, descriptor: UnifiedDependency) = modify(module) {
    it.addDependency(module, descriptor)
  }

  fun updateDependency(module: Module,
             oldDescriptor: UnifiedDependency,
             newDescriptor: UnifiedDependency) = modify(module) {
    it.updateDependency(module, oldDescriptor, newDescriptor)
  }

  fun removeDependency(module: Module, descriptor: UnifiedDependency) = modify(module) {
    it.removeDependency(module, descriptor)
  }

  private fun modify(module: Module, modifier: (ExternalDependencyModificator) -> Unit) {
    return ExternalDependencyModificator.EP_NAME.getExtensionList(myProject).firstOrNull { it.supports(module) }?.let(modifier)
           ?: throw IllegalArgumentException(DependencyUpdaterBundle.message("cannot.modify.module", module.name))
  }

  private fun <T> read(module: Module, reader: (ExternalDependencyModificator) -> List<T>): List<T> {
    return ExternalDependencyModificator.EP_NAME.getExtensionList(myProject)
      .filter{ it.supports(module)}
      .flatMap { reader(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DependencyModifierService {
      return project.getService(DependencyModifierService::class.java)
    }
  }
}