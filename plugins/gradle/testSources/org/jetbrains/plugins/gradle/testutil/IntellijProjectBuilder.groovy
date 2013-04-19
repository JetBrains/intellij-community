package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot
import org.jetbrains.plugins.gradle.util.GradleUtil

/** 
 * @author Denis Zhdanov
 * @since 1/25/12 3:09 PM
 */
class IntellijProjectBuilder extends AbstractProjectBuilder {

  static def LIBRARY_ENTRY_TYPES = [(OrderRootType.CLASSES) : "bin", (OrderRootType.SOURCES) : "src"]
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
    getProjectLibraryTable: { projectLibraryTable },
    getContentRoots: { contentRoots[it] }
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
  protected createContentRoot(module, rootPath, Map paths) {
    new ModuleAwareContentRoot(module, [ getFile: {asVirtualFile(rootPath)} ] as ContentEntry)
  }

  @Override
  protected createModuleDependency(ownerModule, targetModule, scope, boolean exported) {
    def stub = [:]
    def result = stub as ModuleOrderEntry
    stub.accept = { policy, defaultValue -> policy.visitModuleOrderEntry(result, defaultValue) }
    stub.getModule = { targetModule }
    stub.getOwnerModule = { ownerModule }
    stub.getModuleName = { targetModule.name }
    stub.getScope = { scope }
    stub.isExported = { exported }
    result
  }

  @Override
  protected createLibrary(String name, Map paths) {
    libraryPaths[name] = paths
    [
      getName: { name },
      getPresentableName: { name },
      getFiles: {
        type ->
          (libraryPaths[name])[LIBRARY_ENTRY_TYPES[type]].findAll { it }.collect { asVirtualFile(it) }.toArray(DUMMY_VIRTUAL_FILE_ARRAY)
      }
    ] as Library
  }

  @Override
  protected createLibraryDependency(module, library, scope, boolean exported) {
    def stub = [:]
    def result = stub as LibraryOrderEntry
    stub.accept = { policy, defaultValue -> policy.visitLibraryOrderEntry(result, defaultValue) }
    stub.getLibraryName = { library.name }
    stub.getLibrary = { library }
    stub.getOwnerModule = { module }
    stub.getScope = { scope }
    stub.isExported = { exported }
    result
  }

  @Override
  protected applyLibraryPaths(library, Map paths) {
    libraryPaths[library.name] = paths
  }

  @Override
  protected reset() { }

  private static def asVirtualFile(path) {
    [
      getPath: { GradleUtil.toCanonicalPath(path) },
      getFileType: { FileTypes.UNKNOWN }
    ] as VirtualFile
  }
}
