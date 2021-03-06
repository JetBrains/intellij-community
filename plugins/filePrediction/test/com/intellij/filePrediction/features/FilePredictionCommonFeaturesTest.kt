// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features

import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramFeatures
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
import kotlin.math.abs

class FilePredictionCommonFeaturesTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private fun doTestSimilarityFeatures(prevPath: String,
                                       newPath: String,
                                       expected: FileFeaturesProducer) {
    doTestFeatures(
      prevPath, newPath,
      listOf(FilePredictionSimilarityFeatures()),
      expected
    )
  }

  private fun doTestFeatures(prevPath: String, newPath: String,
                             providers: List<FilePredictionFeatureProvider>,
                             expectedFeaturesProvider: FileFeaturesProducer) {
    val prevFile = myFixture.addFileToProject(prevPath, "PREVIOUS FILE")
    val nextFile = myFixture.addFileToProject(newPath, "NEXT FILE")

    val emptyCache = FilePredictionFeaturesCache(FAILED_COMPUTATION, FilePredictionNGramFeatures(emptyMap()))
    val actual: MutableMap<String, FilePredictionFeature> = hashMapOf()
    for (provider in providers) {
      val features = provider.calculateFileFeatures(
        myFixture.project, nextFile.virtualFile, prevFile.virtualFile, emptyCache
      )
      actual.putAll(features)
    }

    val expected = expectedFeaturesProvider.produce(myFixture.project)
    for (feature in expected.entries) {
      assertTrue("Cannot find feature '${feature.key}' in $actual", actual.containsKey(feature.key))

      if (feature.value.value is Double) {
        val expectedValue = feature.value.value as Double
        val actualValue = actual[feature.key]!!.value as Double
        assertTrue(
          "The value of feature '${feature.key}' is different from expected. Expected: $expectedValue, Actual: $actualValue",
          abs(expectedValue - actualValue) < 0.0001
        )
      }
      else {
        assertEquals("The value of feature '${feature.key}' is different from expected", feature.value, actual[feature.key])
      }
    }
  }

  private fun doTestSimilarityFeatures(newPath: String, configurator: ProjectConfigurator, featuresProvider: FileFeaturesProducer) {
    doTestFeatures(FilePredictionSimilarityFeatures(), newPath, configurator, featuresProvider)
  }

  private fun doTestFeatures(provider: FilePredictionFeatureProvider,
                             newPath: String, configurator: ProjectConfigurator,
                             expectedFeaturesProvider: FileFeaturesProducer) {
    val nextFile = myFixture.addFileToProject(newPath, "NEXT FILE")
    configurator.configure(myFixture.project, myModule)

    val emptyCache = FilePredictionFeaturesCache(FAILED_COMPUTATION, FilePredictionNGramFeatures(emptyMap()))
    val actual = provider.calculateFileFeatures(
      myFixture.project, nextFile.virtualFile, null, emptyCache
    )
    val expected = expectedFeaturesProvider.produce(myFixture.project)
    for (feature in expected.entries) {
      assertTrue("Cannot find feature '${feature.key}' in $actual", actual.containsKey(feature.key))
      assertEquals("The value of feature '${feature.key}' is different from expected", feature.value, actual[feature.key])
    }
  }

  fun `test file name with no common word`() {
    doTestSimilarityFeatures(
      "prevSome.txt", "nextFile.txt",
      ConstFileFeaturesProducer(
        "common_words" to FilePredictionFeature.numerical(0),
        "common_words_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test file name with common word`() {
    doTestSimilarityFeatures(
      "prevFile.txt", "nextFile.txt",
      ConstFileFeaturesProducer(
        "common_words" to FilePredictionFeature.numerical(1),
        "common_words_norm" to FilePredictionFeature.numerical(0.5)
      )
    )
  }

  fun `test file name with multiple common words`() {
    doTestSimilarityFeatures(
      "prevFileFoo.txt", "fooNextFileSome.txt",
      ConstFileFeaturesProducer(
        "common_words" to FilePredictionFeature.numerical(2),
        "common_words_norm" to FilePredictionFeature.numerical(0.5714)
      )
    )
  }

  fun `test file name with common words and different length`() {
    doTestSimilarityFeatures(
      "foo.txt", "nextFileSomeFoo.txt",
      ConstFileFeaturesProducer(
        "common_words" to FilePredictionFeature.numerical(1),
        "common_words_norm" to FilePredictionFeature.numerical(0.4)
      )
    )
  }

  fun `test file name prefix for completely different files`() {
    doTestSimilarityFeatures(
      "prevFile.txt", "nextFile.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0),
        "name_prefix_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test file name prefix for files with common prefix`() {
    doTestSimilarityFeatures(
      "myPrevFile.txt", "myNextFile.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(2),
        "name_prefix_norm" to FilePredictionFeature.numerical(0.2)
      )
    )
  }

  fun `test file name prefix for equal files`() {
    doTestSimilarityFeatures(
      "file.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(4),
        "name_prefix_norm" to FilePredictionFeature.numerical(1.0)
      )
    )
  }

  fun `test file name of different length`() {
    doTestSimilarityFeatures(
      "someFile.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0),
        "name_prefix_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test file name with common prefix but different length`() {
    doTestSimilarityFeatures(
      "filePrevious.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(4),
        "name_prefix_norm" to FilePredictionFeature.numerical(0.5)
      )
    )
  }

  fun `test file name in child directory`() {
    doTestSimilarityFeatures(
      "src/someFile.txt", "src/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0),
        "name_prefix_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test file name in neighbour directories`() {
    doTestSimilarityFeatures(
      "src/com/site/ui/someFile.txt", "src/com/site/component/file.txt",
      ConstFileFeaturesProducer(
        "name_prefix" to FilePredictionFeature.numerical(0),
        "name_prefix_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test files path in project root`() {
    doTestSimilarityFeatures(
      "prevFile.txt", "nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(0),
        "relative_path_prefix" to FilePredictionFeature.numerical(0),
        "relative_path_prefix_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test files path in the same directory`() {
    doTestSimilarityFeatures(
      "src/prevFile.txt", "src/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(4),
        "relative_path_prefix" to FilePredictionFeature.numerical(4),
        "relative_path_prefix_norm" to FilePredictionFeature.numerical(0.333333333)
      )
    )
  }

  fun `test files path in the neighbour directories`() {
    doTestSimilarityFeatures(
      "src/ui/prevFile.txt", "src/components/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(4),
        "relative_path_prefix" to FilePredictionFeature.numerical(4),
        "relative_path_prefix_norm" to FilePredictionFeature.numerical(0.21052)
      )
    )
  }

  fun `test files path of different length`() {
    doTestSimilarityFeatures(
      "firstFile.txt", "another/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "path_prefix" to FilePredictionFeature.numerical(0),
        "relative_path_prefix" to FilePredictionFeature.numerical(0),
        "relative_path_prefix_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test common ancestor for files in the same directory`() {
    doTestSimilarityFeatures(
      "src/firstFile.txt", "src/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "relative_common" to FilePredictionFeature.numerical(1),
        "relative_common_norm" to FilePredictionFeature.numerical(1.0),
        "relative_distance" to FilePredictionFeature.numerical(0),
        "relative_distance_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test common ancestor for files with the same name in the same directory`() {
    doTestSimilarityFeatures(
      "src/file.txt", "src/file.java",
      FileFeaturesByProjectPathProducer(
        "relative_common" to FilePredictionFeature.numerical(1),
        "relative_common_norm" to FilePredictionFeature.numerical(1.0),
        "relative_distance" to FilePredictionFeature.numerical(0),
        "relative_distance_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test common ancestor for files in root directory`() {
    doTestSimilarityFeatures(
      "firstFile.txt", "nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "relative_common" to FilePredictionFeature.numerical(0),
        "relative_common_norm" to FilePredictionFeature.numerical(0.0),
        "relative_distance" to FilePredictionFeature.numerical(0),
        "relative_distance_norm" to FilePredictionFeature.numerical(0.0)
      )
    )
  }

  fun `test common ancestor for files in neighbor directory`() {
    doTestSimilarityFeatures(
      "src/ui/firstFile.txt", "src/components/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "relative_common" to FilePredictionFeature.numerical(1),
        "relative_common_norm" to FilePredictionFeature.numerical(0.5),
        "relative_distance" to FilePredictionFeature.numerical(2),
        "relative_distance_norm" to FilePredictionFeature.numerical(0.5)
      )
    )
  }

  fun `test common ancestor for path with different length`() {
    doTestSimilarityFeatures(
      "src/ui/foo/bar/firstFile.txt", "src/components/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "relative_common" to FilePredictionFeature.numerical(1),
        "relative_common_norm" to FilePredictionFeature.numerical(0.3333),
        "relative_distance" to FilePredictionFeature.numerical(4),
        "relative_distance_norm" to FilePredictionFeature.numerical(0.6666)
      )
    )
  }

  fun `test long common ancestor`() {
    doTestSimilarityFeatures(
      "src/ui/foo/firstFile.txt", "src/ui/foo/components/nextFile.txt",
      FileFeaturesByProjectPathProducer(
        "relative_common" to FilePredictionFeature.numerical(3),
        "relative_common_norm" to FilePredictionFeature.numerical(0.8571),
        "relative_distance" to FilePredictionFeature.numerical(1),
        "relative_distance_norm" to FilePredictionFeature.numerical(0.1428)
      )
    )
  }

  fun `test files in the root directory`() {
    doTestSimilarityFeatures(
      "prevFile.txt", "nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(true),
        "same_module" to FilePredictionFeature.binary(true),
        "ancestor" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test files in same child directory`() {
    doTestSimilarityFeatures(
      "src/prevFile.txt", "src/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(true),
        "same_module" to FilePredictionFeature.binary(true),
        "ancestor" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test files in different directories`() {
    doTestSimilarityFeatures(
      "src/prevFile.txt", "test/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(false),
        "same_module" to FilePredictionFeature.binary(true),
        "ancestor" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test previous files in child directory`() {
    doTestSimilarityFeatures(
      "src/sub/prevFile.txt", "src/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(false),
        "same_module" to FilePredictionFeature.binary(true),
        "ancestor" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test new files in child directory`() {
    doTestSimilarityFeatures(
      "src/prevFile.txt", "src/sub/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(false),
        "same_module" to FilePredictionFeature.binary(true),
        "ancestor" to FilePredictionFeature.binary(true)
      )
    )
  }

  fun `test files in neighbor directories`() {
    doTestSimilarityFeatures(
      "src/ui/prevFile.txt", "src/component/nextFile.txt",
      ConstFileFeaturesProducer(
        "same_dir" to FilePredictionFeature.binary(false),
        "same_module" to FilePredictionFeature.binary(true),
        "ancestor" to FilePredictionFeature.binary(false)
      )
    )
  }

  fun `test file not in a source root`() {
    doTestSimilarityFeatures(
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
    doTestSimilarityFeatures(
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
    doTestSimilarityFeatures(
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
    doTestSimilarityFeatures(
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
    doTestSimilarityFeatures(
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
    doTestSimilarityFeatures(
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