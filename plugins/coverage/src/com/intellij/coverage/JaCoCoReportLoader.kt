// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.coverage.analysis.AnalysisUtils
import com.intellij.coverage.analysis.ClassFilesLocator
import com.intellij.coverage.analysis.ModuleRequest
import com.intellij.coverage.analysis.collectOutputRoots
import com.intellij.coverage.analysis.getWorkingThreads
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.IClassCoverage
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.tools.ExecFileLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val LOG = Logger.getInstance(JaCoCoReportLoader::class.java)

internal object JaCoCoReportLoader {
  @JvmStatic
  @Throws(IOException::class)
  fun loadReport(
    project: Project,
    modules: List<Module>,
    suites: List<JavaCoverageSuite>,
    reportFiles: List<Path>,
    loader: ExecFileLoader,
    reporter: CoverageLoadErrorReporter,
  ): CoverageBuilder = runBlockingMaybeCancellable {
    val workingThreads = getWorkingThreads()
    val dispatcher = Dispatchers.IO.limitedParallelism(workingThreads)
    withContext(dispatcher) {
      for (reportFile in reportFiles) {
        Files.newInputStream(reportFile).use(loader::load)
      }
    }

    // must analyze test dirs, as inline function calls might be only in tests
    val requests = collectOutputRoots(project, modules, suites, includeTests = true)
    val executionDataStore = loader.executionDataStore
    val results = coroutineScope {
      requests.map { request ->
        async(dispatcher) {
          analyzeOutputRoot(request, executionDataStore, suites, reporter)
        }
      }.awaitAll()
    }
    val classes = LinkedHashMap<String, IClassCoverage>()
    for (result in results) {
      for (classCoverage in result.classes) {
        val existing = classes[classCoverage.name]
        if (existing == null ||
            // prefer the version from executionDataStore
            existing.id != classCoverage.id
            && executionDataStore.get(existing.id) == null
            && executionDataStore.get(classCoverage.id) != null) {
          classes[classCoverage.name] = classCoverage
        }
      }
    }
    val coverageBuilder = CoverageBuilder()
    classes.values.forEach(coverageBuilder::visitCoverage)
    coverageBuilder
  }
}

private fun analyzeOutputRoot(
  request: ModuleRequest,
  executionDataStore: ExecutionDataStore,
  suites: List<JavaCoverageSuite>,
  reporter: CoverageLoadErrorReporter,
): CoverageBuilder {
  val coverageBuilder = CoverageBuilder()
  val analyzer = Analyzer(executionDataStore, coverageBuilder)
  ClassFilesLocator.findClassFiles(request.root, request.packages).use { classFiles ->
    for (classFile in classFiles) {
      val internalName = AnalysisUtils.buildVMName(classFile.packageVMName, classFile.simpleName)
      val className = AnalysisUtils.internalNameToFqn(internalName)
      if (suites.none { it.isClassFiltered(className) }) continue

      val classBytes = classFile.loadBytes()
      if (classBytes == null) {
        val warning = IOException("Could not read class file: ${classFile.path}")
        LOG.info(warning)
        reporter.reportWarning(warning)
        continue
      }
      try {
        analyzer.analyzeClass(classBytes, classFile.relativePath)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.info(e)
        reporter.reportWarning(e)
      }
    }
  }
  return coverageBuilder
}
