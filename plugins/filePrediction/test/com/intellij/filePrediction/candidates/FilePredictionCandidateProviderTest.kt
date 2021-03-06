package com.intellij.filePrediction.candidates

import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestProjectBuilder
import com.intellij.filePrediction.predictor.model.unregisterCandidateProvider
import com.intellij.filePrediction.references.ExternalReferencesResult
import com.intellij.filePrediction.references.FilePredictionReferencesHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class FilePredictionCandidateProviderTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  override fun isCommunity(): Boolean = true

  override fun getBasePath(): String {
    return "${FilePredictionTestDataHelper.defaultTestData}/candidates"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val testName = super.getTestName(lowercaseFirstLetter)
    return testName.replace("_", "/")
  }

  private fun doTestRecent(builder: FilePredictionTestProjectBuilder, vararg expected: Pair<String, String>) {
    doTestInternal(builder.openMain(), FilePredictionRecentFilesProvider(), 5, *expected)
  }

  private fun doTestRefs(builder: FilePredictionTestProjectBuilder, vararg expected: String) {
    val expectedWithSource = expected.map { it to "reference" }.toTypedArray()
    doTestInternal(builder, FilePredictionReferenceProvider(), 5, *expectedWithSource)
  }

  private fun doTestNeighbor(builder: FilePredictionTestProjectBuilder, vararg expected: String) {
    val expectedWithSource = expected.map { it to "neighbor" }.toTypedArray()
    doTestInternal(builder, FilePredictionNeighborFilesProvider(), 10, *expectedWithSource)
  }

  private fun doTest(builder: FilePredictionTestProjectBuilder, vararg expected: Pair<String, String>) {
    unregisterCandidateProvider(FilePredictionRecentSessionsProvider())
    doTestInternal(builder, CompositeCandidateProvider(), 10, *expected)
  }

  private fun doTestInternal(builder: FilePredictionTestProjectBuilder,
                             provider: FilePredictionCandidateProvider,
                             limit: Int,
                             vararg expected: Pair<String, String>) {
    val root = builder.create(myFixture)
    assertNotNull("Cannot create test project", root)

    val file = FilePredictionTestDataHelper.findMainTestFile(root)
    assertNotNull("Cannot find file with '${FilePredictionTestDataHelper.DEFAULT_MAIN_FILE}' name", file)

    val result: ExternalReferencesResult = ApplicationManager.getApplication().executeOnPooledThread(Callable {
      FilePredictionReferencesHelper.calculateExternalReferences(myFixture.project, file!!).value
    }).get(1, TimeUnit.SECONDS)
    val candidates = provider.provideCandidates(myFixture.project, file, result.references, limit)

    val actual = candidates.map {
      FileUtil.getRelativePath(root.path, it.file.path, '/') to StringUtil.toLowerCase(it.source.name)
    }.toSet()
    assertEquals(ContainerUtil.newHashSet(*expected), actual)
  }

  fun testReference_single() {
    val builder =
      FilePredictionTestProjectBuilder().addFile(
        "com/test/MainTest.java", "import com.test.ui.Baz;"
      ).addFile("com/test/ui/Baz.java")
    doTestRefs(builder, "com/test/ui/Baz.java")
  }

  fun testReference_multiple() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.ui.Baz;
        import com.test.component.Foo;
        import com.test.Helper;
      """.trimIndent()).addFiles(
        "com/test/Helper.java",
        "com/test/ui/Baz.java",
        "com/test/component/Foo.java"
      )

    doTestRefs(
      builder,
      "com/test/Helper.java",
      "com/test/ui/Baz.java",
      "com/test/component/Foo.java"
    )
  }

  fun testReference_moreThanLimit() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.component.Foo1;
        import com.test.component.Foo2;
        import com.test.component.Foo3;
        import com.test.component.Foo4;
        import com.test.component.Foo5;
        import com.test.component.Foo6;
        import com.test.component.Foo7;
        import com.test.component.Foo8;
        import com.test.component.Foo9;
        import com.test.component.Foo10;
      """.trimIndent()).addFiles(
        "com/test/component/Foo1.java",
        "com/test/component/Foo2.java",
        "com/test/component/Foo3.java",
        "com/test/component/Foo4.java",
        "com/test/component/Foo5.java",
        "com/test/component/Foo6.java",
        "com/test/component/Foo7.java",
        "com/test/component/Foo8.java",
        "com/test/component/Foo9.java",
        "com/test/component/Foo10.java"
      )

    doTestRefs(
      builder,
      "com/test/component/Foo1.java",
      "com/test/component/Foo2.java",
      "com/test/component/Foo3.java",
      "com/test/component/Foo4.java",
      "com/test/component/Foo5.java"
    )
  }

  fun testReference_anotherPackage() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import org.another.component.Foo1;
        import org.another.component.Foo2;
        import org.another.component.Foo3;
        import org.another.component.Foo4;
        import org.another.component.Foo5;
        import org.another.component.Foo6;
        import org.another.component.Foo7;
        import org.another.component.Foo8;
        import org.another.component.Foo9;
        import org.another.component.Foo10;
      """.trimIndent()).addFiles(
        "org/another/component/Foo1.java",
        "org/another/component/Foo2.java",
        "org/another/component/Foo3.java",
        "org/another/component/Foo4.java",
        "org/another/component/Foo5.java",
        "org/another/component/Foo6.java",
        "org/another/component/Foo7.java",
        "org/another/component/Foo8.java",
        "org/another/component/Foo9.java",
        "org/another/component/Foo10.java"
      )

    doTestRefs(
      builder,
      "org/another/component/Foo1.java",
      "org/another/component/Foo2.java",
      "org/another/component/Foo3.java",
      "org/another/component/Foo4.java",
      "org/another/component/Foo5.java"
    )
  }

  fun testNeighbor_single() {
    val builder =
      FilePredictionTestProjectBuilder().addFiles(
        "com/test/MainTest.txt",
        "com/test/Foo.txt"
      )
    doTestNeighbor(builder, "com/test/Foo.txt")
  }

  fun testNeighbor_multiple() {
    val builder =
      FilePredictionTestProjectBuilder().addFiles(
        "com/test/MainTest.txt",
        "com/test/Foo.txt",
        "com/Bar.csv"
      )
    doTestNeighbor(
      builder,
      "com/test/Foo.txt",
      "com/Bar.csv"
    )
  }

  fun testNeighbor_sameDir() {
    val builder =
      FilePredictionTestProjectBuilder().addFiles(
        "com/test/MainTest.txt",
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt"
      )
    doTestNeighbor(
      builder,
      "com/test/Foo1.txt",
      "com/test/Foo2.txt",
      "com/test/Foo3.txt",
      "com/test/Foo4.txt"
    )
  }

  fun testNeighbor_parentDir() {
    val builder =
      FilePredictionTestProjectBuilder().addFiles(
        "com/test/MainTest.txt",
        "com/Foo1.txt",
        "com/Foo2.txt",
        "com/Foo3.txt"
      )
    doTestNeighbor(
      builder,
      "com/Foo1.txt",
      "com/Foo2.txt",
      "com/Foo3.txt"
    )
  }

  fun testNeighbor_moreThanLimit() {
    val builder =
      FilePredictionTestProjectBuilder().addFiles(
        "com/test/MainTest.txt",
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt",
        "com/test/Foo6.txt",
        "com/test/Foo7.txt",
        "com/test/Foo8.txt",
        "com/test/Foo9.txt",
        "com/test/Foo10.txt",
        "com/test/Foo11.txt",
        "com/test/Foo12.txt"
      )
    doTestNeighbor(
      builder,
      "com/test/Foo1.txt",
      "com/test/Foo2.txt",
      "com/test/Foo3.txt",
      "com/test/Foo4.txt",
      "com/test/Foo5.txt",
      "com/test/Foo6.txt",
      "com/test/Foo7.txt",
      "com/test/Foo8.txt",
      "com/test/Foo9.txt",
      "com/test/Foo10.txt"
    )
  }

  fun testRecent_single() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/ui/Baz.java")
    doTestRecent(builder, "com/test/ui/Baz.java" to "open")
  }

  fun testRecent_multiple() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/ui/Baz.java")
      .open("com/test/component/Bar.java")
      .open("com/test/Foo.java")
    doTestRecent(
      builder,
      "com/test/ui/Baz.java" to "open",
      "com/test/component/Bar.java" to "open",
      "com/test/Foo.java" to "open"
    )
  }

  fun testRecent_notOpened() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/ui/Baz.java")
      .open("com/test/component/Bar.java")
      .close("com/test/component/Bar.java")
      .open("com/test/Foo.java")
      .close("com/test/ui/Baz.java")
      .close("com/test/Foo.java")
    doTestRecent(
      builder,
      "com/test/ui/Baz.java" to "recent",
      "com/test/component/Bar.java" to "recent",
      "com/test/Foo.java" to "recent"
    )
  }

  fun testRecent_opened() {
    val builder = FilePredictionTestProjectBuilder("com")
      .open("com/test/Foo1.java")
      .open("com/test/Foo2.java")
      .open("com/test/Foo3.java").close("com/test/Foo3.java")
      .open("com/test/Foo4.java").close("com/test/Foo4.java")
      .open("com/test/Foo5.java").close("com/test/Foo5.java")
      .open("com/test/Foo6.java").close("com/test/Foo6.java")
      .open("com/test/Foo7.java").close("com/test/Foo7.java")
      .open("com/test/Foo8.java").close("com/test/Foo8.java")
      .open("com/test/Foo9.java").close("com/test/Foo9.java")
      .open("com/test/Foo10.java").close("com/test/Foo10.java")
      .open("com/test/Foo11.java").close("com/test/Foo11.java")
      .open("com/test/Foo12.java").close("com/test/Foo12.java")
      .open("com/test/Foo13.java").close("com/test/Foo13.java")
      .open("com/test/Foo14.java").close("com/test/Foo14.java")
      .open("com/test/Foo15.java").close("com/test/Foo15.java")
    doTestRecent(
      builder,
      "com/test/Foo1.java" to "open",
      "com/test/Foo2.java" to "open",
      "com/test/Foo13.java" to "recent",
      "com/test/Foo14.java" to "recent",
      "com/test/Foo15.java" to "recent"
    )
  }

  fun testComposite_sameDir() {
    val builder =
      FilePredictionTestProjectBuilder().addFile(
        "com/test/MainTest.java", "import com.test.Helper;"
      ).addFiles(
        "com/test/Helper.java",
        "com/test/Foo.txt"
      )
    doTest(
      builder,
      "com/test/Helper.java" to "reference",
      "com/test/Foo.txt" to "neighbor"
    )
  }

  fun testComposite_childDirs() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.ui.Baz;
        import com.test.component.Foo;
        import com.test.Helper;
        """.trimIndent()
      ).addFiles(
        "com/test/Helper.java",
        "com/test/Foo.txt",
        "com/test/ui/Baz.java",
        "com/test/component/Foo.java"
      )
    doTest(
      builder,
      "com/test/Helper.java" to "reference",
      "com/test/ui/Baz.java" to "reference",
      "com/test/component/Foo.java" to "reference",
      "com/test/Foo.txt" to "neighbor"
    )
  }

  fun testComposite_childAndParentsDirs() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.ui.Baz;
        import com.test.component.Foo;
        import com.test.Helper;
        """.trimIndent()
      ).addFiles(
        "com/Bar.txt",
        "com/test/Foo.txt",
        "com/test/Helper.java",
        "com/test/ui/Baz.java",
        "com/test/component/Foo.java"
      )
    doTest(
      builder,
      "com/test/Helper.java" to "reference",
      "com/test/ui/Baz.java" to "reference",
      "com/test/component/Foo.java" to "reference",
      "com/test/Foo.txt" to "neighbor",
      "com/Bar.txt" to "neighbor"
    )
  }

  fun testComposite_anotherPackage() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.ui.Baz;
        import com.test.component.Foo;
        import com.test.Helper;
        """.trimIndent()
      ).addFiles(
        "org/NotIncludedFile.txt",
        "com/Bar.txt",
        "com/test/Foo.txt",
        "com/test/Helper.java",
        "com/test/ui/Baz.java",
        "com/test/component/Foo.java"
      )
    doTest(
      builder,
      "com/test/Helper.java" to "reference",
      "com/test/ui/Baz.java" to "reference",
      "com/test/component/Foo.java" to "reference",
      "com/test/Foo.txt" to "neighbor",
      "com/Bar.txt" to "neighbor"
    )
  }

  fun testComposite_moreThanLimitRef() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.component.Foo1;
        import com.test.component.Foo2;
        import com.test.component.Foo3;
        import com.test.component.Foo4;
        import com.test.component.Foo5;
        import com.test.component.Foo6;
        import com.test.component.Foo7;
        import com.test.component.Foo8;
        import com.test.component.Foo9;
        import com.test.component.Foo10;
        """.trimIndent()
      ).addFiles(
        "com/test/Neighbor.txt",
        "com/test/component/Foo1.java",
        "com/test/component/Foo2.java",
        "com/test/component/Foo3.java",
        "com/test/component/Foo4.java",
        "com/test/component/Foo5.java",
        "com/test/component/Foo6.java",
        "com/test/component/Foo7.java",
        "com/test/component/Foo8.java",
        "com/test/component/Foo9.java",
        "com/test/component/Foo10.java"
      )
    doTest(
      builder,
      "com/test/component/Foo1.java" to "reference",
      "com/test/component/Foo2.java" to "reference",
      "com/test/component/Foo3.java" to "reference",
      "com/test/Neighbor.txt" to "neighbor"
    )
  }

  fun testComposite_moreThanLimitNeighbor() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.ui.Baz;
        """.trimIndent()
      ).addFiles(
        "com/test/ui/Baz.java",
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt",
        "com/test/Foo6.txt",
        "com/test/Foo7.txt",
        "com/test/Foo8.txt",
        "com/test/Foo9.txt",
        "com/test/Foo10.txt",
        "com/test/Foo11.txt",
        "com/test/Foo12.txt"
      )
    doTest(
      builder,
      "com/test/ui/Baz.java" to "reference",
      "com/test/Foo1.txt" to "neighbor",
      "com/test/Foo2.txt" to "neighbor",
      "com/test/Foo3.txt" to "neighbor",
      "com/test/Foo4.txt" to "neighbor"
    )
  }

  fun testComposite_moreThanLimit() {
    val builder =
      FilePredictionTestProjectBuilder().addFile("com/test/MainTest.java", """
        import com.test.ui.Baz1;
        import com.test.ui.Baz2;
        import com.test.ui.Baz3;
        import com.test.ui.Baz4;
        import com.test.ui.Baz5;
        import com.test.ui.Baz6;
        import com.test.ui.Baz7;
        import com.test.ui.Baz8;
        import com.test.ui.Baz9;
        import com.test.ui.Baz10;
        """.trimIndent()
      ).addFiles(
        "com/test/ui/Baz1.java",
        "com/test/ui/Baz2.java",
        "com/test/ui/Baz3.java",
        "com/test/ui/Baz4.java",
        "com/test/ui/Baz5.java",
        "com/test/ui/Baz6.java",
        "com/test/ui/Baz7.java",
        "com/test/ui/Baz8.java",
        "com/test/ui/Baz9.java",
        "com/test/ui/Baz10.java",
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt",
        "com/test/Foo6.txt",
        "com/test/Foo7.txt",
        "com/test/Foo8.txt",
        "com/test/Foo9.txt",
        "com/test/Foo10.txt",
        "com/test/Foo11.txt",
        "com/test/Foo12.txt"
      )
    doTest(
      builder,
      "com/test/ui/Baz1.java" to "reference",
      "com/test/ui/Baz2.java" to "reference",
      "com/test/ui/Baz3.java" to "reference",
      "com/test/Foo1.txt" to "neighbor",
      "com/test/Foo2.txt" to "neighbor",
      "com/test/Foo3.txt" to "neighbor"
    )
  }

  fun testComposite_differentSources() {
    val builder =
      FilePredictionTestProjectBuilder().addFile(
        "com/test/MainTest.java", "import com.test.Helper;"
      ).addFiles(
        "com/test/Helper.java",
        "com/test/Foo.txt"
      ).open("com/test/Foo.txt"
      ).open("com/test/ui/Component.java").openMain()
    doTest(
      builder,
      "com/test/Helper.java" to "neighbor",
      "com/test/Foo.txt" to "neighbor",
      "com/test/ui/Component.java" to "open"
    )
  }
}