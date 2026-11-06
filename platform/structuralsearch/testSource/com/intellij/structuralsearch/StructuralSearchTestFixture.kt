// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch

import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.structuralsearch.plugin.ui.Configuration
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import com.intellij.structuralsearch.plugin.ui.UIUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import java.io.File
import java.util.function.Function

/**
 * Creates a [StructuralSearchTestFixture] on the project of [projectFixture].
 * Declare the result as an instance property, so that each test gets new [StructuralSearchTestFixture.options].
 * Declare [projectFixture] in the companion as an opened project.
 * A Java test sets up the mock JDK and the language level on this project itself.
 * [loadFile][StructuralSearchTestFixture.loadFile] reads its files from [testDataPath].
 */
@TestOnly
@JvmOverloads
fun structuralSearchFixture(projectFixture: TestFixture<Project>, testDataPath: String? = null): TestFixture<StructuralSearchTestFixture> =
  testFixture("structuralSearchFixture") {
    initialized(StructuralSearchTestFixture(projectFixture.init(), testDataPath)) {}
  }

/**
 * The match options and the search helpers of a structural search test.
 * The helpers compile and match a pattern in a read action, so a test can call them off the EDT.
 */
@TestOnly
class StructuralSearchTestFixture internal constructor(val project: Project, private val testDataPath: String?) {
  /**
   * The options of [findMatches]. Each test gets new options with recursive search.
   */
  var options: MatchOptions = MatchOptions().apply { isRecursiveSearch = true }

  fun getSearchPlan(query: String, fileType: LanguageFileType): String {
    val matchOptions = MatchOptions()
    matchOptions.fillSearchCriteria(query)
    matchOptions.setFileType(fileType)
    return inReadAction {
      PatternCompiler.compilePattern(project, matchOptions, true, true)
      PatternCompiler.getLastSearchPlan()
    }
  }

  fun findMatchesCount(`in`: String, pattern: String, fileType: LanguageFileType): Int = findMatches(`in`, pattern, fileType).size

  fun findMatches(
    `in`: String,
    pattern: String,
    patternFileType: LanguageFileType,
    patternLanguage: Language?,
    sourceFileType: LanguageFileType,
    physicalSourceFile: Boolean,
  ): List<MatchResult> {
    options.fillSearchCriteria(pattern)
    options.setFileType(patternFileType)
    options.dialect = patternLanguage
    return inReadAction {
      val compiledPattern = PatternCompiler.compilePattern(project, options, true, false)
      val message = checkApplicableConstraints(options, compiledPattern)
      assertNull(message, message)
      val matcher = Matcher(project, options, compiledPattern)
      matcher.testFindMatches(`in`, true, sourceFileType, physicalSourceFile)
    }
  }

  fun findMatches(`in`: String, pattern: String, fileType: LanguageFileType): List<MatchResult> =
    findMatches(`in`, pattern, fileType, null, fileType, false)

  fun findMatchesText(`in`: String, pattern: String, fileType: LanguageFileType, vararg expectedResults: String) {
    val matches = findMatches(`in`, pattern, fileType)
    val actualResults = inReadAction { matches.map { StructuralSearchUtil.getPresentableElement(it.match).text } }
    assertEquals(expectedResults.size, actualResults.size, actualResults.joinToString("\n"))
    for (i in expectedResults.indices) {
      assertEquals(expectedResults[i], actualResults[i])
    }
  }

  /**
   * Matches the predefined [template] in [source], and checks the text of the presentable element of each match.
   */
  fun checkPredefinedConfiguration(template: Configuration?, source: String, fileType: LanguageFileType, vararg results: String) {
    checkPredefinedConfiguration(template, source, fileType, { e ->
      StructuralSearchUtil.getPresentableElement(e).text
    }, *results)
  }

  /**
   * Matches the predefined [template] in [source], and checks the result of [resultConverter] for each match.
   * [template] replaces [options].
   */
  fun checkPredefinedConfiguration(
    template: Configuration?,
    source: String,
    fileType: LanguageFileType,
    resultConverter: Function<in PsiElement, String>,
    vararg expectedResults: String,
  ) {
    val searchConfiguration = assertInstanceOf(SearchConfiguration::class.java, template)
    options = searchConfiguration.matchOptions
    val actualResults = inReadAction {
      val matcher = Matcher(project, options)
      matcher.testFindMatches(source, true, fileType, false).map { resultConverter.apply(it.match) }
    }
    assertEquals(listOf(*expectedResults), actualResults, searchConfiguration.name)
  }

  /**
   * Runs [action] in a read action in a coroutine, because the type evaluation of some languages calls `runBlockingCancellable`.
   */
  private fun <T> inReadAction(action: () -> T): T = timeoutRunBlocking { readActionBlocking(action) }

  fun loadFile(fileName: String): String {
    val path = checkNotNull(testDataPath) { "Pass the test data path to structuralSearchFixture" }
    return FileUtilRt.loadFile(File(path + fileName), CharsetToolkit.UTF8, true)
  }

  companion object {
    @JvmStatic
    fun checkApplicableConstraints(options: MatchOptions, compiledPattern: CompiledPattern): String? {
      return ReadAction.computeBlocking<String?, RuntimeException> { findNotApplicableConstraint(options, compiledPattern) }
    }

    private fun findNotApplicableConstraint(options: MatchOptions, compiledPattern: CompiledPattern): String? {
      val profile = checkNotNull(StructuralSearchUtil.getProfileByFileType(options.fileType)) {
        "no profile found for file type: ${options.fileType}"
      }
      for (varName in options.variableConstraintNames) {
        val nodes = compiledPattern.getVariableNodes(varName)
        val constraint = options.getVariableConstraint(varName)
        val usedConstraints = mutableListOf<String>()
        if (constraint.regExp.isNotEmpty()) {
          usedConstraints.add(UIUtil.TEXT)
        }
        if (constraint.isWithinHierarchy) {
          usedConstraints.add(UIUtil.TEXT_HIERARCHY)
        }
        if (constraint.minCount == 0) {
          usedConstraints.add(UIUtil.MINIMUM_ZERO)
        }
        if (constraint.maxCount > 1) {
          usedConstraints.add(UIUtil.MAXIMUM_UNLIMITED)
        }
        if (constraint.nameOfExprType.isNotEmpty()) {
          usedConstraints.add(UIUtil.TYPE)
        }
        if (constraint.nameOfFormalArgType.isNotEmpty()) {
          usedConstraints.add(UIUtil.EXPECTED_TYPE)
        }
        if (constraint.referenceConstraint.isNotEmpty()) {
          usedConstraints.add(UIUtil.REFERENCE)
        }
        for (usedConstraint in usedConstraints) {
          if (!profile.isApplicableConstraint(usedConstraint, nodes, false, constraint.isPartOfSearchResults)) {
            return "$usedConstraint not applicable for $varName"
          }
        }
      }
      return null
    }
  }
}
