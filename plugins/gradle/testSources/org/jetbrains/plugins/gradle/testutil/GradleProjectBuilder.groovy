package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.model.project.ContentRootData

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 1:29 PM
 */
class GradleProjectBuilder extends AbstractProjectBuilder {

  @Override
  protected createProject(String name, LanguageLevel languageLevel) {
    def result = new ProjectData(same, same, id)
    result.name = name
    result.languageLevel = languageLevel
    result
  }

  @Override
  protected createModule(String name) {
    registerModule(new ModuleData(name, unique))
  }

  @Override
  protected registerModule(module) {
    project.addModule(module)
    module
  }

  @Override
  protected createContentRoot(module, rootPath, Map paths) {
    def result = new ContentRootData(module, rootPath)
    module.addContentRoot(result)
    return result
  }

  @Override
  protected createModuleDependency(ownerModule, targetModule, scope, boolean exported) {
    def result = new ModuleDependencyData(ownerModule, targetModule)
    ownerModule.addDependency(result)
    result.setScope(scope)
    result.setExported(exported)
    result
  }

  @Override
  protected createLibrary(String name, Map paths) {
    def result = new LibraryData(name)
    applyLibraryPaths(result, paths)
    result
  }

  @Override
  protected createLibraryDependency(module, library, scope, boolean exported) {
    def result = new LibraryDependencyData(module, library)
    module.addDependency(result)
    result.setScope(scope)
    result.setExported(exported)
    result
  }

  @Override
  protected applyLibraryPaths(library, Map paths) {
    library.forgetAllPaths()
    ['bin': LibraryPathType.BINARY, 'src': LibraryPathType.SOURCE, 'doc': LibraryPathType.DOC].each {
      key, type -> paths[key]?.each { library.addPath(type, it) }
    }
    project.addLibrary(library)
  }

  @Override
  protected reset() {
    modulesCache.values().each { it.clearDependencies(); it.clearContentRoots() }
  }
}
