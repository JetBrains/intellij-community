package org.jetbrains.plugins.gradle.testutil

import org.jetbrains.plugins.gradle.model.GradleProject
import org.jetbrains.plugins.gradle.model.GradleModule
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency
import org.jetbrains.plugins.gradle.model.GradleLibrary
import org.jetbrains.plugins.gradle.model.LibraryPathType
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
    (paths.bin?: [same]).each { result.addPath(LibraryPathType.BINARY, it) }
    (paths.src?: [same]).each { result.addPath(LibraryPathType.SOURCE, it) }
    (paths.doc?: [same]).each { result.addPath(LibraryPathType.DOC, it) }
    result
  }

  @Override
  protected createLibraryDependency(module, library) {
    def result = new GradleLibraryDependency(module, library)
    module.addDependency(result)
    result
  }
}
