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
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.ModuleOrderEntry

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 3:09 PM
 */
class IntellijProjectBuilder extends AbstractProjectBuilder {

  static def LIBRARY_ENTRY_TYPES = [(OrderRootType.CLASSES) : "bin"]
  static VirtualFile[] DUMMY_VIRTUAL_FILE_ARRAY = new VirtualFile[0]

  def projectStub = [getName: { same }]
  def project = projectStub as Project

  def projectLibraryTableStub = [ getLibraries: { libraries.values() as Library[] } ]
  def projectLibraryTable = projectLibraryTableStub as LibraryTable
  
  def platformFacade = [
    getModules: { modules.values() },
    getOrderEntries: { libraryDependencies[it] + moduleDependencies[it] },
    getProjectIcon: { IconLoader.getIcon("/nodes/ideaProject.png") },
    getLocalFileSystemPath: { it.path },
    getProjectLibraryTable: { projectLibraryTable }
  ]
  /** (library name - (library root type - paths)). */
  def libraryPaths = [:].withDefault { [:] }

  @Override
  protected createProject(String name, LanguageLevel languageLevel) {
    projectStub.getName = { name }
    platformFacade.getLanguageLevel = { languageLevel } as Closure
    project
  }

  @Override
  protected createModule(String name) {
    [ getName: { name } ] as Module
  }

  @Override
  protected registerModule(Object module) { }

  @Override
  protected createModuleDependency(ownerModule, targetModule) {
    def stub = [:]
    def result = stub as ModuleOrderEntry
    stub.accept = { policy, defaultValue -> policy.visitModuleOrderEntry(result, defaultValue) }
    stub.getModule = { targetModule }
    stub.getOwnerModule = { ownerModule }
    stub.getModuleName = { targetModule.name }
    result
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

  @Override
  protected reset() { }
}
