// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history.context

import com.intellij.filePrediction.features.FilePredictionFeature
import com.intellij.filePrediction.features.FilePredictionFeature.Companion.binary
import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestProjectBuilder
import com.intellij.filePrediction.features.ConstFileFeaturesProducer
import com.intellij.filePrediction.features.FileFeaturesProducer
import com.intellij.filePrediction.features.FilePredictionFeaturesCache
import com.intellij.filePrediction.features.history.FilePredictionHistoryBaseTest
import com.intellij.filePrediction.features.history.ngram.FilePredictionNGramFeatures
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.filePrediction.references.ExternalReferencesResult.Companion.FAILED_COMPUTATION
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider

class FilePredictionContextFeaturesTest : FilePredictionHistoryBaseTest() {

  private fun doTest(builder: FilePredictionTestProjectBuilder, vararg expected: Pair<String, FilePredictionFeature>) {
    doTestContextFeatures(builder, ConstFileFeaturesProducer(*expected))
  }

  private fun doTestContextFeatures(builder: FilePredictionTestProjectBuilder, featuresProvider: FileFeaturesProducer) {
    val root = builder.create(myFixture)
    assertNotNull("Cannot create test project", root)

    val file = FilePredictionTestDataHelper.findMainTestFile(root)
    assertNotNull("Cannot find main project file", file)

    val manager = FileEditorManager.getInstance(myFixture.project)

    val prevFile = manager.selectedEditor?.file
    assertTrue("Cannot open main file because it's already opened", prevFile != file)

    val provider = FilePredictionContextFeatures()
    val emptyCache = FilePredictionFeaturesCache(FAILED_COMPUTATION, FilePredictionNGramFeatures(emptyMap()))
    val actual = provider.calculateFileFeatures(myFixture.project, file!!, prevFile, emptyCache)
    val expected = featuresProvider.produce(myFixture.project)
    for (feature in expected.entries) {
      assertTrue("Cannot find feature '${feature.key}' in $actual", actual.containsKey(feature.key))
      assertEquals("The value of feature '${feature.key}' is different from expected", feature.value, actual[feature.key])
    }
  }

  fun `test no opened files`() {
    val builder = FilePredictionTestProjectBuilder("com")
    doTest(
      builder,
      "opened" to binary(false)
    )
  }

  fun `test single opened file`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(false)
    )
  }

  fun `test opened main file`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .openMain()
      .open("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(true)
    )
  }

  fun `test several opened files`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/Foo.txt")
      .open("com/test/Bar.txt")
    doTest(
      builder,
      "opened" to binary(false)
    )
  }

  fun `test main and several other opened files`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/Foo.txt")
      .openMain()
      .open("com/test/Bar.txt")
    doTest(
      builder,
      "opened" to binary(true)
    )
  }

  fun `test closed file`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .openMain()
      .closeMain()
    doTest(
      builder,
      "opened" to binary(false)
    )
  }

  fun `test re-opened file`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .openMain().closeMain().openMain()
      .open("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(true)
    )
  }

  fun `test select already opened file`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .openMain()
      .open("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(true)
    )
  }

  fun `test switching between opened file`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .openMain()
      .open("com/test/Foo.txt")
      .selectMain()
      .select("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(true)
    )
  }

  fun `test switching between opened file without main`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/Bar.txt")
      .open("com/test/Foo.txt")
      .select("com/test/Bar.txt")
      .select("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(false)
    )
  }

  fun `test switching between opened file with closed main`() {
    val builder = FilePredictionTestProjectBuilder("com")
      .openMain()
      .open("com/test/Foo.txt")
      .selectMain().closeMain()
      .open("com/test/Bar.txt")
      .select("com/test/Foo.txt")
    doTest(
      builder,
      "opened" to binary(false)
    )
  }
}