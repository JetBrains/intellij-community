// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.rules

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.rd.attach
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.createHeavyProject
import com.intellij.testFramework.runInEdtAndWait
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ProjectModelRule : TestRule {
  val baseProjectDir = TempDirectory()
  private val projectDelegate = lazy { createHeavyProject(baseProjectDir.root.toPath()) }
  private val disposableRule = DisposableRule()
  val project by projectDelegate
  private val closeProject = object : ExternalResource() {
    override fun after() {
      if (projectDelegate.isInitialized()) {
        runInEdtAndWait {
          ProjectManagerEx.getInstanceEx().forceCloseProject(project)
        }
      }
    }
  }
  private val ruleChain = RuleChain(baseProjectDir, closeProject, disposableRule)

  override fun apply(base: Statement, description: Description): Statement {
    return ruleChain.apply(base, description)
  }

  fun createModule(name: String = "module"): Module {
    val imlFile = baseProjectDir.newFile("$name/$name.iml")
    return runWriteActionAndWait {
      ModuleManager.getInstance(project).newModule(imlFile.systemIndependentPath, EmptyModuleType.EMPTY_MODULE)
    }
  }

  fun createSdk(name: String = "sdk"): Sdk {
    return ProjectJdkTable.getInstance().createSdk(name, sdkType)
  }

  fun addSdk(sdk: Sdk): Sdk {
    runWriteActionAndWait {
      ProjectJdkTable.getInstance().addJdk(sdk, disposableRule.disposable)
    }
    return sdk
  }

  fun addProjectLevelLibrary(name: String, setup: (Library.ModifiableModel) -> Unit = {}): Library {
    return addLibrary(name, projectLibraryTable, setup)
  }

  private fun addLibrary(name: String, libraryTable: LibraryTable, setup: (Library.ModifiableModel) -> Unit): Library {
    val model = libraryTable.modifiableModel
    val library = model.createLibrary(name)
    val libraryModel = library.modifiableModel
    setup(libraryModel)
    runWriteActionAndWait {
      libraryModel.commit()
      model.commit()
    }
    return library
  }

  fun addApplicationLevelLibrary(name: String): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    val library = addLibrary(name, libraryTable) {}
    disposableRule.disposable.attach {
      runWriteActionAndWait {
        if (libraryTable.getLibraryByName(name) == library) {
          libraryTable.removeLibrary(library)
        }
      }
    }
    return library
  }

  val sdkType: SdkTypeId
    get() = SimpleJavaSdkType.getInstance()

  val projectRootManager: ProjectRootManager
    get() = ProjectRootManager.getInstance(project)

  val projectLibraryTable: LibraryTable
    get() = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
}
