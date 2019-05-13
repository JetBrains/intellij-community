// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant

import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.writeChild

class TestDataFileReferenceTest : TestDataReferenceTestCase() {

  fun testDataFileResolve() {
    val javaFile = myContentRootSubdir.writeChild("TestClass.java", "some java code here")

    myFixture.configureByText("ATest.java", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

      @com.intellij.testFramework.TestDataPath("${"\$"}CONTENT_ROOT/contentRootSubdir/")
      public class ATest extends LightCodeInsightFixtureTestCase {
        protected void doTest() {
          configureByFile("TestClass.java");
        }

        void configureByFile(@com.intellij.testFramework.TestDataFile String file){}
      }
    """.trimIndent())

    assertResolvedTo(javaFile, "TestClass.java")
  }

  fun testRelativePathDataFileResolve() {
    val dir = VfsTestUtil.createDir(myContentRootSubdir, "bar")
    val javaFile = dir.writeChild("TestClass.java", "some java code here")

    myFixture.configureByText("ATest.java", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

      @com.intellij.testFramework.TestDataPath("${"\$"}CONTENT_ROOT/contentRootSubdir/")
      public class ATest extends LightCodeInsightFixtureTestCase {
        protected void doTest() {
          configureByFile("bar/TestClass.java");
        }

        void configureByFile(@com.intellij.testFramework.TestDataFile String file){}
      }
    """.trimIndent())

    assertResolvedTo(dir, "bar")
    assertResolvedTo(javaFile, "TestClass.java")
  }

  fun testDataFileResolveVarags() {
    val testClass1 = myContentRootSubdir.writeChild("TestClass1.java", "some java code here")
    val testClass2 = myContentRootSubdir.writeChild("TestClass2.java", "some java code here")

    myFixture.configureByText("ATest.java", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

      @com.intellij.testFramework.TestDataPath("${"\$"}CONTENT_ROOT/contentRootSubdir/")
      public class ATest extends LightCodeInsightFixtureTestCase {
        protected void doTest() {
          configureByFiles("TestClass1.java", "TestClass2.java");
        }

        void configureByFiles(@com.intellij.testFramework.TestDataFile String... files){}
      }
    """.trimIndent())

    assertResolvedTo(testClass1, "TestClass1.java")
    assertResolvedTo(testClass2, "TestClass2.java")
  }

}


