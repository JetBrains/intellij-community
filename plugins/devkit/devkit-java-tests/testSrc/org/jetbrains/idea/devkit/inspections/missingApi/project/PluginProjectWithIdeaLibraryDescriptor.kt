// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.project

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.module.PluginModuleType
import java.io.File

/**
 * Descriptor of an IDEA plugin project with configured IDEA library (not JDK).
 */
class PluginProjectWithIdeaLibraryDescriptor : LightProjectDescriptor() {

  companion object {
    private const val IDEA_LIBRARY_NAME = "IDEA library"

    fun disposeIdeaLibrary(project: Project) {
      val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      val library = runReadAction {
        libraryTable.getLibraryByName(IDEA_LIBRARY_NAME)
      } ?: return

      runWriteAction {
        libraryTable.removeLibrary(library)
      }
    }
  }

  override fun getModuleType(): ModuleType<*> = PluginModuleType.getInstance()

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)

    val moduleExtension = model.getModuleExtension(LanguageLevelModuleExtension::class.java)
    moduleExtension.languageLevel = LanguageLevel.HIGHEST

    val library = createIdeaLibrary(module)
    model.addLibraryEntry(library)
  }

  private fun createIdeaLibrary(module: Module): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
    val library = runWriteAction {
      libraryTable.createLibrary(IDEA_LIBRARY_NAME)
    }

    runWriteAction {
      val modifiableModel = library.modifiableModel

      modifiableModel.addIdeaJarContainingClassToClassPath(Editor::class.java)
      modifiableModel.addIdeaJarContainingClassToClassPath(BaseState::class.java)

      modifiableModel.commit()
    }
    return library
  }

  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()

  private fun Library.ModifiableModel.addIdeaJarContainingClassToClassPath(clazz: Class<*>) {
    val jarFile = File(FileUtil.toSystemIndependentName(PathManager.getJarPathForClass(clazz)!!))
    val virtualFile = VfsUtil.findFileByIoFile(jarFile, true)
    addRoot(virtualFile!!, OrderRootType.CLASSES)
  }

}