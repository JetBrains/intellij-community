package org.jetbrains.plugins.gradle.testutil

import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.module.Module

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.LibraryOrderEntry

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.plugins.gradle.util.GradleUtil

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 3:09 PM
 */
class IntellijProjectBuilder extends AbstractProjectBuilder {

  static def LIBRARY_ENTRY_TYPES = [(OrderRootType.CLASSES) : "bin"]
  static VirtualFile[] DUMMY_VIRTUAL_FILE_ARRAY = new VirtualFile[0]
  
  def projectStub = [:]
  def project = projectStub as Project
  def platformFacade = [
    getModules: { modules },
    getOrderEntries: { dependencies[it] },
    getProjectIcon: { IconLoader.getIcon("/nodes/ideaProject.png") },
    getLocalFileSystemPath: { it.path }
  ]
  /** (library name - (library root type - paths)). */
  def libraryPaths = [:].withDefault { [:] }

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
    libraryPaths[name] = paths
    [
      getName: { name },
      getPresentableName: { name },
      getFiles: {
        type -> (libraryPaths[name])[LIBRARY_ENTRY_TYPES[type]].findAll { it }.collect {
          String path = it
          [getPath: { GradleUtil.toCanonicalPath(path) }] as VirtualFile
        }.toArray(DUMMY_VIRTUAL_FILE_ARRAY)
      }
    ] as Library
  }

  @Override
  protected createLibraryDependency(module, library) {
    def stub = [:]
    def result = stub as LibraryOrderEntry
    stub.accept = { policy, defaultValue -> policy.visitLibraryOrderEntry(result, defaultValue) }
    stub.getLibraryName = { library.name }
    stub.getLibrary = { library }
    stub.getOwnerModule = { module }
    result
  }

  @Override
  protected applyLibraryPaths(library, Map paths) {
    libraryPaths[library.name] = paths
  }
}
