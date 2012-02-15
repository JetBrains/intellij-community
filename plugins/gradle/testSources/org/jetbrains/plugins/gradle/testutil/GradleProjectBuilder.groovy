package org.jetbrains.plugins.gradle.testutil

import org.jetbrains.plugins.gradle.model.gradle.GradleProject
import org.jetbrains.plugins.gradle.model.gradle.GradleModule
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType
import com.intellij.pom.java.LanguageLevel

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
    def result = new GradleModule(name, unique)
    project.addModule(result)
    result
  }

  @Override
  protected createLibrary(String name, Map paths) {
    def result = new GradleLibrary(name)
    applyLibraryPaths(result, paths)
    result
  }

  @Override
  protected createLibraryDependency(module, library) {
    def result = new GradleLibraryDependency(module, library)
    module.addDependency(result)
    result
  }

  @Override
  protected applyLibraryPaths(library, Map paths) {
    library.forgetAllPaths()
    ['bin': LibraryPathType.BINARY, 'src': LibraryPathType.SOURCE, 'doc': LibraryPathType.DOC].each {
      key, type -> paths[key]?.each { library.addPath(type, it) }
    }
  }
}
