package org.jetbrains.plugins.gradle.testutil

import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.module.Module

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.LibraryOrderEntry

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 3:09 PM
 */
class IntellijProjectBuilder extends AbstractProjectBuilder {
  
  def projectStub = [:]
  def project = projectStub as Project
  def platformFacade = [
    getModules: { modules },
    getOrderEntries: { dependencies[it] },
    getProjectIcon: { IconLoader.getIcon("/nodes/ideaProject.png") }
  ]

  @Override
  protected createProject(String name, LanguageLevel languageLevel) {
    projectStub.getName = { name }
    platformFacade.getLanguageLevel = { languageLevel }
    project
  }

  @Override
  protected createModule(String name) {
    [ getName: { name } ] as Module
  }

  @Override
  protected createLibrary(String name, Map paths) {
    [ getName: { name }, getPresentableName: { name } ] as Library
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
