package com.intellij.filePrediction.candidate

import com.intellij.filePrediction.CompositeCandidateProvider
import com.intellij.filePrediction.FilePredictionFeaturesHelper
import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestProjectBuilder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.util.containers.ContainerUtil

class FilePredictionCandidateProviderTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  override fun isCommunity(): Boolean = true

  override fun getBasePath(): String {
    return "${FilePredictionTestDataHelper.defaultTestData}/candidates"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val testName = super.getTestName(lowercaseFirstLetter)
    return testName.replace("_", "/")
  }

  private fun doTest(builder: FilePredictionTestProjectBuilder, vararg expected: String) {
    val root = builder.create(myFixture)
    assertNotNull("Cannot create test project", root)

    val file = FilePredictionTestDataHelper.findMainTestFile(root)
    assertNotNull("Cannot find file with '${FilePredictionTestDataHelper.DEFAULT_MAIN_FILE}' name", file)

    val result = FilePredictionFeaturesHelper.calculateExternalReferences(myFixture.project, file!!).value
    val candidates = CompositeCandidateProvider.provideCandidates(myFixture.project, file, result.references, 10)

    val actual = candidates.map { FileUtil.getRelativePath(root.path, it.path, '/') }.toSet()
    assertEquals(ContainerUtil.newHashSet(*expected), actual)
  }

  fun testReference_single() {
    val builder =
      FilePredictionTestProjectBuilder().addFile(
        "com/test/MainTest.java", "import com.test.ui.Baz;"
      ).addFile("com/test/ui/Baz.java")
    doTest(builder, "com/test/ui/Baz.java")
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

    doTest(
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

    doTest(
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

    doTest(
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
    doTest(builder, "com/test/Foo.txt")
  }

  fun testNeighbor_multiple() {
    val builder =
      FilePredictionTestProjectBuilder().addFiles(
        "com/test/MainTest.txt",
        "com/test/Foo.txt",
        "com/Bar.csv"
      )
    doTest(
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
    doTest(
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
    doTest(
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
    doTest(
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
      "com/test/Helper.java",
      "com/test/Foo.txt"
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
      "com/test/Helper.java",
      "com/test/ui/Baz.java",
      "com/test/component/Foo.java",
      "com/test/Foo.txt"
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
      "com/test/Helper.java",
      "com/test/ui/Baz.java",
      "com/test/component/Foo.java",
      "com/test/Foo.txt",
      "com/Bar.txt"
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
      "com/test/Helper.java",
      "com/test/ui/Baz.java",
      "com/test/component/Foo.java",
      "com/test/Foo.txt",
      "com/Bar.txt"
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
      "com/test/component/Foo1.java",
      "com/test/component/Foo2.java",
      "com/test/component/Foo3.java",
      "com/test/component/Foo4.java",
      "com/test/component/Foo5.java",
      "com/test/Neighbor.txt"
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
      "com/test/ui/Baz.java",
      "com/test/Foo1.txt",
      "com/test/Foo2.txt",
      "com/test/Foo3.txt",
      "com/test/Foo4.txt",
      "com/test/Foo5.txt",
      "com/test/Foo6.txt",
      "com/test/Foo7.txt",
      "com/test/Foo8.txt",
      "com/test/Foo9.txt"
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
      "com/test/ui/Baz1.java",
      "com/test/ui/Baz2.java",
      "com/test/ui/Baz3.java",
      "com/test/ui/Baz4.java",
      "com/test/ui/Baz5.java",
      "com/test/Foo1.txt",
      "com/test/Foo2.txt",
      "com/test/Foo3.txt",
      "com/test/Foo4.txt",
      "com/test/Foo5.txt"
    )
  }
}