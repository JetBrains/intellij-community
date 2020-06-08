// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.references.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.io.File

class FilePredictionGeneralFeaturesTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private fun doTestGeneralFeatures(prevPath: String, newPath: String, featuresProvider: FileFeaturesProducer) {
    val prevFile = myFixture.addFileToProject(prevPath, "PREVIOUS FILE")
    val nextFile = myFixture.addFileToProject(newPath, "NEXT FILE")

    val provider = FilePredictionGeneralFeatures()
    val actual = provider.calculateFileFeatures(
      myFixture.project, nextFile.virtualFile, prevFile.virtualFile, FAILED_COMPUTATION
    )
    val expected = featuresProvider.produce(myFixture.project)
    for (feature in expected.entries) {
      assertTrue("Cannot find feature '${feature.key}' in $actual", actual.containsKey(feature.key))
      assertEquals("The value of feature '${feature.key}' is different from expected", feature.value, actual[feature.key])
    }
  }

  private fun doTestGeneralFeatures(newPath: String, configurator: ProjectConfigurator, featuresProvider: FileFeaturesProducer) {
    val nextFile = myFixture.addFileToProject(newPath, "NEXT FILE")
    configurator.configure(myFixture.project, myModule)

    val provider = FilePredictionGeneralFeatures()
    val actual = provider.calculateFileFeatures(
      myFixture.project, nextFile.virtualFile, null, FAILED_COMPUTATION
    )
    val expected = featuresProvider.produce(myFixture.project)
    for (feature in expected.entries) {
      assertTrue("Cannot find feature '${feature.key}' in $actual", actual.containsKey(feature.key))
      assertEquals("The value of feature '${feature.key}' is different from expected", feature.value, actual[feature.key])
    }
  }

  fun `test file name prefix for completely different files`() {
    doTestGeneralFeatures(
      "prevFile.txt", "nextFile.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0)
      )
    )
  }

  fun `test file name prefix for files with common prefix`() {
    doTestGeneralFeatures(
      "myPrevFile.txt", "myNextFile.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(2)
      )
    )
  }

  fun `test file name prefix for equal files`() {
    doTestGeneralFeatures(
      "file.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(4)
      )
    )
  }

  fun `test file name of different length`() {
    doTestGeneralFeatures(
      "someFile.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0)
      )
    )
  }

  fun `test file name in child directory`() {
    doTestGeneralFeatures(
      "src/someFile.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0)
      )
    )
  }

  fun `test file name in neighbour directories`() {
    doTestGeneralFeatures(
      "src/com/site/ui/someFile.txt", "src/com/site/component/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0)
      )
    )
  }

  fun `test files path in project root`() {
    doTestGeneralFeatures(
      "prevFile.txt", "nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(0)
      )
    )
  }

  fun `test files path in the same directory`() {
    doTestGeneralFeatures(
      "src/prevFile.txt", "src/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(4)
      )
    )
  }

  fun `test files path in the neighbour directories`() {
    doTestGeneralFeatures(
      "src/ui/prevFile.txt", "src/components/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(4)
      )
    )
  }

  fun `test files path of different length`() {
    doTestGeneralFeatures(
      "firstFile.txt", "another/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(0)
      )
    )
  }

  fun `test files in the root directory`() {
    doTestGeneralFeatures(
      "prevFile.txt", "nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(true),
        "same_module" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test files in same child directory`() {
    doTestGeneralFeatures(
      "src/prevFile.txt", "src/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(true),
        "same_module" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test files in different directories`() {
    doTestGeneralFeatures(
      "src/prevFile.txt", "test/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(false),
        "same_module" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test file not in a source root`() {
    doTestGeneralFeatures(
      "file.txt",
      object : ProjectConfigurator {
        override fun configure(project: Project, module: Module) {
          TestProjectStructureConfigurator.removeSourceRoot(module)
        }
      },
      ConstFileFeaturesProducer(
        "in_project" to FilePredictionFeature.binary(true),
        "in_source" to FilePredictionFeature.binary(false),
        "in_library" to FilePredictionFeature.binary(false),
        "excluded" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test file in source root`() {
    doTestGeneralFeatures(
      "nextFile.txt",
      EmptyProjectConfigurator,
      ConstFileFeaturesProducer(
        "in_project" to FilePredictionFeature.binary(true),
        "in_source" to FilePredictionFeature.binary(true),
        "in_library" to FilePredictionFeature.binary(false),
        "excluded" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test file not in a custom source root`() {
    doTestGeneralFeatures(
      "nextFile.txt",
      object : ProjectConfigurator {
        override fun configure(project: Project, module: Module) {
          TestProjectStructureConfigurator.removeSourceRoot(module)
          TestProjectStructureConfigurator.addSourceRoot(module, "src", false)
        }
      },
      ConstFileFeaturesProducer(
        "in_project" to FilePredictionFeature.binary(true),
        "in_source" to FilePredictionFeature.binary(false),
        "in_library" to FilePredictionFeature.binary(false),
        "excluded" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test file in a custom source root`() {
    doTestGeneralFeatures(
      "src/nextFile.txt",
      object : ProjectConfigurator {
        override fun configure(project: Project, module: Module) {
          TestProjectStructureConfigurator.removeSourceRoot(module)
          TestProjectStructureConfigurator.addSourceRoot(module, "src", false)
        }
      },
      ConstFileFeaturesProducer(
        "in_project" to FilePredictionFeature.binary(true),
        "in_source" to FilePredictionFeature.binary(true),
        "in_library" to FilePredictionFeature.binary(false),
        "excluded" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test file in a library source root`() {
    doTestGeneralFeatures(
      "lib/nextFile.txt",
      object : ProjectConfigurator {
        override fun configure(project: Project, module: Module) {
          TestProjectStructureConfigurator.removeSourceRoot(module)
          TestProjectStructureConfigurator.addLibrary(module, "lib", OrderRootType.SOURCES)
        }
      },
      ConstFileFeaturesProducer(
        "in_project" to FilePredictionFeature.binary(true),
        "in_source" to FilePredictionFeature.binary(true),
        "in_library" to FilePredictionFeature.binary(true),
        "excluded" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test file in library classes`() {
    doTestGeneralFeatures(
      "lib/nextFile.txt",
      object : ProjectConfigurator {
        override fun configure(project: Project, module: Module) {
          TestProjectStructureConfigurator.removeSourceRoot(module)
          TestProjectStructureConfigurator.addLibrary(module, "lib", OrderRootType.CLASSES)
        }
      },
      ConstFileFeaturesProducer(
        "in_project" to FilePredictionFeature.binary(false),
        "in_source" to FilePredictionFeature.binary(false),
        "in_library" to FilePredictionFeature.binary(true),
        "excluded" to FilePredictionFeature.binary(false)
      )
    )
  }
}

private object EmptyProjectConfigurator : ProjectConfigurator {
  override fun configure(project: Project, module: Module) = Unit
}

private interface ProjectConfigurator {
  fun configure(project: Project, module: Module)
}

private object TestProjectStructureConfigurator {
  fun addSourceRoot(module: Module, path: String, isTest: Boolean) {
    val project = module.project
    val dir = project.guessProjectDir()?.path
    CodeInsightFixtureTestCase.assertNotNull(dir)

    val fullPath = "${dir}${File.separator}$path"

    ApplicationManager.getApplication().runWriteAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val contentEntry = model.contentEntries.find {
        it.file?.let { file -> FileUtil.isAncestor(file.path, fullPath, false) } ?: false
      }

      val rootType = if (isTest) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
      val properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("")
      val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, fullPath)
      contentEntry?.addSourceFolder(url, rootType, properties)

      model.commit()
    }
  }

  fun addLibrary(module: Module, path: String, type: OrderRootType) {
    val project = module.project
    val dir = project.guessProjectDir()?.path
    CodeInsightFixtureTestCase.assertNotNull(dir)
    val fullPath = "${dir}${File.separator}$path"

    ApplicationManager.getApplication().runWriteAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val libraryModel = model.moduleLibraryTable.modifiableModel

      val modifiableModel = libraryModel.createLibrary("test_library").modifiableModel
      val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, fullPath)
      modifiableModel.addRoot(url, type)

      modifiableModel.commit()
      model.commit()
    }
  }

  fun removeSourceRoot(module: Module) {
    ApplicationManager.getApplication().runWriteAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val contentEntry = model.contentEntries[0]
      contentEntry.removeSourceFolder(contentEntry.sourceFolders[0])
      model.commit()
    }
  }
}