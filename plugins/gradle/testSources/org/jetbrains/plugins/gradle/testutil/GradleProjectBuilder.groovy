package org.jetbrains.plugins.gradle.testutil

import org.jetbrains.plugins.gradle.model.gradle.GradleProject
import org.jetbrains.plugins.gradle.model.gradle.GradleModule
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.model.gradle.GradleModuleDependency
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 1:29 PM
 */
class GradleProjectBuilder extends AbstractProjectBuilder {

  @Override
  protected createProject(String name, LanguageLevel languageLevel) {
    def result = new GradleProject(same, same)
    result.name = name
    result.languageLevel = languageLevel
    result
  }

  @Override
  protected createModule(String name) {
    registerModule(new GradleModule(name, unique))
  }

  @Override
  protected registerModule(module) {
    project.addModule(module)
    module
  }

  @Override
  protected createContentRoot(module, rootPath, Map paths) {
    def result = new GradleContentRoot(module, rootPath)
    module.addContentRoot(result)
    return result
  }

  @Override
  protected createModuleDependency(ownerModule, targetModule, scope, boolean exported) {
    def result = new GradleModuleDependency(ownerModule, targetModule)
    ownerModule.addDependency(result)
    result.setScope(scope)
    result.setExported(exported)
    result
  }

  @Override
  protected createLibrary(String name, Map paths) {
    def result = new GradleLibrary(name)
    applyLibraryPaths(result, paths)
    project.addLibrary(result)
    result
  }

  @Override
  protected createLibraryDependency(module, library, scope, boolean exported) {
    def result = new GradleLibraryDependency(module, library)
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
  }

  @Override
  protected reset() {
    modulesCache.values().each { it.clearDependencies(); it.clearContentRoots() }
  }
}
