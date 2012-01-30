package org.jetbrains.plugins.gradle.testutil

import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.RootPolicy
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.LibraryOrderEntry
import groovy.mock.interceptor.StubFor
import com.intellij.openapi.project.Project

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 3:09 PM
 */
class IntellijProjectBuilder extends AbstractProjectBuilder {
  
  def projectStub = [:]
  def project = projectStub as Project
  def projectStructureHelper = [
    getModules: { modules },
    getOrderEntries: { dependencies[it] }
  ]

  @Override
  protected createProject(String name, LanguageLevel languageLevel) {
    projectStub.getName = { name }
    projectStructureHelper.getLanguageLevel = { languageLevel }
    project
  }

  @Override
  protected createModule(String name) {
    [ getName: { name } ] as Module
  }

  @Override
  protected createLibrary(String name, Map paths) {
    [ getName: { name } ] as Library
  }

  @Override
  protected createLibraryDependency(module, library) {
    def stub = [:]
    def result = stub as LibraryOrderEntry
    stub.accept = { policy, defaultValue -> policy.visitLibraryOrderEntry(result, defaultValue) }
    stub.getLibraryName = { library.name }
    result
  }
}
