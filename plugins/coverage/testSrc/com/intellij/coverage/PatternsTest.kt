// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.util.io.FileUtil
import com.intellij.rt.coverage.data.ProjectData
import com.intellij.rt.coverage.util.CoverageReport
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.regex.Pattern

@RunWith(JUnit4::class)
class PatternsTest : HeavyPlatformTestCase() {
  @Test
  fun `test class patterns should persist in a report file and suite`() = test(listOf("c.C", "a.*"), listOf(), false)

  @Test
  fun `test patterns should persist in a report file and suite`() = test(listOf("a.*"), listOf("b.*"), false)

  @Test
  fun `test empty patterns should persist in a report file and suite`() = test(listOf(), listOf(), false)

  @Test
  fun `test null patterns should persist in a report file and suite`() = test(null, null, false)

  @Test
  fun `test patterns from report file should not overwrite suite`() = test(listOf("a.*"), listOf("b.*"), true)

  @Test
  fun `test suite bundle should merge include patterns`() = testBundle(listOf("a1.*"), listOf("b1.*"), listOf("a2.*"), listOf("b2.*"))

  @Test
  fun `test suite bundle should not merge include patterns when empty`() = testBundle(listOf("a1.*"), listOf("b1.*"),
                                                                                      listOf(), listOf("b2.*"))

  @Test
  fun `test suite bundle should not merge include patterns when null`() = testBundle(listOf("a1.*"), listOf("b1.*"),
                                                                                     null, listOf("b2.*"))

  private fun testBundle(includes1: List<String>?, excludes1: List<String>?, includes2: List<String>?, excludes2: List<String>?) {
    val suite1 = createSuite(includes1, excludes1)
    val suite2 = createSuite(includes2, excludes2)

    val bundle = CoverageSuitesBundle(arrayOf(suite1, suite2))
    val data = bundle.coverageData!!

    if (includes1.isNullOrEmpty() || includes2.isNullOrEmpty()) {
      // include patterns are merged, as suite2 include patterns are empty
      assertTrue(data.incudePatterns.isNullOrEmpty())
    }
    else {
      // include patterns are merged
      TestCase.assertEquals((includes1.toSet() + includes2.toSet()).toList(), convertFromPatterns(data.incudePatterns))
    }

    // exclude patterns are not merged
    assertTrue(data.excludePatterns.isNullOrEmpty())
  }

  private fun test(includes: List<String>?, excludes: List<String>?, saveToSuite: Boolean) {
    val suite = createSuite(includes, excludes, saveToSuite)
    val data = suite.getCoverageData(null)!!
    if (!saveToSuite) {
      TestCase.assertEquals(includes.orEmpty(), convertFromPatterns(data.incudePatterns))
      TestCase.assertEquals(excludes.orEmpty(), convertFromPatterns(data.excludePatterns))
    }
    TestCase.assertEquals(includes.orEmpty(), suite.includeFilters?.toList())
    TestCase.assertEquals(excludes.orEmpty(), suite.excludePatterns?.toList())
  }

  private fun createSuite(includes: List<String>?, excludes: List<String>?, saveToSuite: Boolean = false): JavaCoverageSuite {
    val data = ProjectData()
    data.setIncludePatterns(convertPatterns(includes))
    data.excludePatterns = convertPatterns(excludes)

    val file = FileUtil.createTempFile("coverage", ".ic")
    file.deleteOnExit()
    CoverageReport.save(data, file, null)

    val engine = JavaCoverageEngine.getInstance()
    val runner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    return engine.createSuite(runner, file.name, DefaultCoverageFileProvider(file),
                              if (saveToSuite) includes?.toTypedArray() else null,
                              if (saveToSuite) excludes?.toTypedArray() else null,
                              -1, false, true, false, myProject)
  }

  private fun convertPatterns(includes: List<String>?): List<Pattern>? = includes?.let {
    IDEACoverageRunner.convertToPatterns(it.toTypedArray()).map(Pattern::compile)
  }

  private fun convertFromPatterns(patterns: List<Pattern>?) = patterns?.let {
    IDEACoverageRunner.convertFromPatterns(it.map(Pattern::pattern).toTypedArray()).toList()
  }

}
