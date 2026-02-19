/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof

import com.intellij.diagnostic.hprof.analysis.AnalysisConfig
import com.intellij.diagnostic.hprof.analysis.AnalysisContext
import com.intellij.diagnostic.hprof.analysis.AnalyzeGraph
import com.intellij.diagnostic.hprof.analysis.ClassNomination
import com.intellij.diagnostic.hprof.classstore.HProfMetadata
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.util.IntList
import com.intellij.diagnostic.hprof.util.ListProvider
import com.intellij.diagnostic.hprof.util.UByteList
import com.intellij.diagnostic.hprof.util.UShortList
import com.intellij.diagnostic.hprof.visitors.RemapIDsVisitor
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Assert
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

open class HProfScenarioRunner(
  private val tmpFolder: TemporaryFolder,
  private val remapInMemory: Boolean,
) {

  val regex = Regex("^com\\.intellij\\.diagnostic\\.hprof\\..*\\\$.*\\\$")

  open fun adjustConfig(config: AnalysisConfig): AnalysisConfig = config

  open fun mapClassName(clazz: Class<*>): String {
    // Simplify inner class names
    return clazz.name.replace(regex, "")
  }

  fun createReport(
    scenario: HProfBuilder.() -> Unit,
    nominatedClassNames: List<String>?,
    shouldMapClassNames: Boolean = true,
    config: AnalysisConfig? = null,
  ): String {
    val hprofFile = tmpFolder.newFile()
    HProfTestUtils.createHProfOnFile(hprofFile,
                                     scenario) { c -> if (shouldMapClassNames) mapClassName(c) else c.name }
    return createReport(hprofFile, nominatedClassNames, config)
  }

  fun run(
    scenario: HProfBuilder.() -> Unit,
    baselineFileName: String,
    nominatedClassNames: List<String>?,
    shouldMapClassNames: Boolean = true,
    config: AnalysisConfig? = null,
  ) {
    val report = createReport(scenario, nominatedClassNames, shouldMapClassNames, config)
    compareReportToBaseline(report, baselineFileName)
  }

  fun createReport(
    hprofFile: File,
    nominatedClassNames: List<String>? = null,
    config: AnalysisConfig? = null,
  ): String {
    FileChannel.open(hprofFile.toPath(), StandardOpenOption.READ).use { hprofChannel ->

      val progress = object : AbstractProgressIndicatorBase() {
      }
      progress.isIndeterminate = false

      val parser = HProfEventBasedParser(hprofChannel)
      val hprofMetadata = HProfMetadata.create(parser)
      val histogram = Histogram.create(parser, hprofMetadata.classStore)
      val nominatedClasses = ClassNomination(histogram, 5).nominateClasses()

      val remapIDsVisitor = if (remapInMemory)
        RemapIDsVisitor.createMemoryBased()
      else
        RemapIDsVisitor.createFileBased(openTempEmptyFileChannel(), histogram.instanceCount)

      parser.accept(remapIDsVisitor, "id mapping")
      val idMapper = remapIDsVisitor.getIDMapper()
      parser.setIDMapper(idMapper)
      hprofMetadata.remapIds(idMapper)

      val navigator = ObjectNavigator.createOnAuxiliaryFiles(
        parser,
        openTempEmptyFileChannel(),
        openTempEmptyFileChannel(),
        hprofMetadata,
        histogram.instanceCount
      )

      val parentList = MemoryBackedIntList(navigator.instanceCount.toInt() + 1)
      val sizesList = MemoryBackedIntList(navigator.instanceCount.toInt() + 1)
      val visitedList = MemoryBackedIntList(navigator.instanceCount.toInt() + 1)
      val refIndexList = MemoryBackedUByteList(navigator.instanceCount.toInt() + 1)

      val nominatedClassNamesLocal = nominatedClassNames ?: nominatedClasses.map { it.classDefinition.name }
      val analysisConfig = AnalysisConfig(
        perClassOptions = AnalysisConfig.PerClassOptions(
          classNames = nominatedClassNamesLocal,
          treeDisplayOptions = AnalysisConfig.TreeDisplayOptions.all()
        ),
        histogramOptions = AnalysisConfig.HistogramOptions(
          includeByCount = true,
          includeBySize = false,
          classByCountLimit = Int.MAX_VALUE
        ),
        disposerOptions = AnalysisConfig.DisposerOptions(
          includeDisposerTree = false,
          includeDisposerTreeSummary = false,
          includeDisposedObjectsDetails = false,
          includeDisposedObjectsSummary = false
        ),
        metaInfoOptions = AnalysisConfig.MetaInfoOptions(
          include = false
        ),
        dominatorTreeOptions = AnalysisConfig.DominatorTreeOptions(
          includeDominatorTree = false
        )
      ).let { adjustConfig(it) }
      val analysisContext = AnalysisContext(
        navigator,
        analysisConfig,
        parentList,
        sizesList,
        visitedList,
        refIndexList,
        histogram
      )

      return AnalyzeGraph(analysisContext, memoryBackedListProvider).analyze(progress).mainReport.toString()
    }
  }

  fun compareReportToBaseline(analysisReport: String, baselineFileName: String) {
    val baselinePath = getBaselinePath(baselineFileName)
    val baseline = getBaselineContents(baselinePath)
    Assert.assertEquals("Report doesn't match the baseline from file:\n$baselinePath",
                        baseline,
                        analysisReport)

  }

  /**
   * Get the contents of the baseline file, with system-dependent line endings
   */
  private fun getBaselineContents(path: Path): String {
    return String(Files.readAllBytes(path), StandardCharsets.UTF_8)
      .replace(Regex("(\r\n|\n)"), System.lineSeparator())
  }

  /**
   * Returns path to a baseline file. Baselines may be different for different runtime versions.
   */
  private fun getBaselinePath(fileName: String): Path {
    val javaSpecString = System.getProperty("java.specification.version")
    val filenameWithPath = "diagnostic/analysis-baseline/$fileName"
    val file = File(filenameWithPath)

    val name = file.nameWithoutExtension
    val extension = if (file.extension != "") "." + file.extension else ""
    val testDataDir = file.parentFile

    val javaVersion = javaSpecString.toIntOrNull()
    val minimalVersionToScan = 17 // to be compatible with tests on old jre-s
    if (javaVersion == null || javaVersion <= minimalVersionToScan) {
      val javaSpecSpecificFile = testDataDir.findTestDataFileByJavaSpec(name, javaSpecString, extension)
      if (javaSpecSpecificFile != null) {
        return javaSpecSpecificFile
      }
    }
    else {
      for (v in javaVersion downTo minimalVersionToScan) {
        return testDataDir.findTestDataFileByJavaSpec(name, v.toString(), extension) ?: continue
      }
    }
    return Path.of(PlatformTestUtil.getPlatformTestDataPath(), filenameWithPath)
  }

  private fun File.findTestDataFileByJavaSpec(baseName: String, javaSpec: String, extension: String): Path? {
    val javaSpecSpecificFileName = File(this, "$baseName.$javaSpec$extension").toString()
    val javaSpecSpecificFile = Path.of(PlatformTestUtil.getPlatformTestDataPath(), javaSpecSpecificFileName);
    if (Files.exists(javaSpecSpecificFile)) {
      return javaSpecSpecificFile
    }
    return null
  }

  class MemoryBackedIntList(size: Int) : IntList {
    private val array = IntArray(size)

    override fun get(index: Int): Int = array[index]
    override fun set(index: Int, value: Int) {
      array[index] = value
    }
  }

  class MemoryBackedUShortList(size: Int) : UShortList {
    private val array = ShortArray(size)

    override fun get(index: Int): Int = array[index].toInt()
    override fun set(index: Int, value: Int) {
      array[index] = value.toShort()
    }
  }

  class MemoryBackedUByteList(size: Int) : UByteList {
    private val array = ShortArray(size)

    override fun get(index: Int): Int = array[index].toInt()
    override fun set(index: Int, value: Int) {
      array[index] = value.toShort()
    }
  }

  object memoryBackedListProvider : ListProvider {
    override fun createUByteList(name: String, size: Long) = MemoryBackedUByteList(size.toInt())
    override fun createUShortList(name: String, size: Long) = MemoryBackedUShortList(size.toInt())
    override fun createIntList(name: String, size: Long) = MemoryBackedIntList(size.toInt())
  }

  private fun openTempEmptyFileChannel(): FileChannel {
    return FileChannel.open(tmpFolder.newFile().toPath(),
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.DELETE_ON_CLOSE)
  }
}
