// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NewKotlinFileActionTest : BasePlatformTestCase() {
  private val testTemplate = CustomFileTemplate("test template", "kt").apply {
    text = """
        #if (${'$'}{PACKAGE_NAME} && ${'$'}{PACKAGE_NAME} != "")package ${'$'}{PACKAGE_NAME}
        
        #end
        class ${'$'}{NAME}
    """.trimIndent()
  }

  fun testClassName() = doTest(
    "MyClass",
    "",
    "MyClass.kt",
    "",
    "MyClass"
  )

  fun testClassNameAndExtension() = doTest(
    "Foo.kt",
    "",
    "Foo.kt",
    "",
    "Foo"
  )

  fun testPackageAndClassName() = doTest(
    "com.my.MyBar",
    "/com/my",
    "MyBar.kt",
    "com.my",
    "MyBar"
  )

  fun testClassNameAndSuffix() = doTest(
    "MyClass.suffix",
    "",
    "MyClass.suffix.kt",
    "",
    "MyClass"
  )

  fun testPackageAndClassNameAndSuffix() = doTest(
    "com.my.MyClass.suffix",
    "/com/my",
    "MyClass.suffix.kt",
    "com.my",
    "MyClass"
  )

  fun testPackageAndClassNameAndSuffixAndExtension() = doTest(
    "com.my.MyClass.suffix.kt",
    "/com/my",
    "MyClass.suffix.kt",
    "com.my",
    "MyClass"
  )

  fun testPathAndClassName() = doTest(
    "com/my/MyClass",
    "/com/my",
    "MyClass.kt",
    "com.my",
    "MyClass"
  )

  fun testPathAndClassNameAndSuffixAndExtension() = doTest(
    "com/my/MyClass.suffix.kt",
    "/com/my",
    "MyClass.suffix.kt",
    "com.my",
    "MyClass"
  )

  fun testUppercasePackageAndClassNameAndThreeSuffixes() = doTest(
    "com.my.Pack.foo.MyClass.suffix1.suffix2.suffix3",
    "/com/my/Pack/foo",
    "MyClass.suffix1.suffix2.suffix3.kt",
    "com.my.Pack.foo",
    "MyClass"
  )

  private fun doTest(
    userInput: String,
    expectedDirPath: String,
    expectedFileName: String,
    expectedPackage: String,
    expectedClassName: String
  ) {
    val srcDir = myFixture.psiManager.findDirectory(myFixture.tempDirFixture.findOrCreateDir("."))!!
    val file = createKotlinFileFromTemplateForTest(userInput, testTemplate, srcDir)!!

    assertEquals(expectedDirPath, file.virtualFile.parent.path.removePrefix(srcDir.virtualFile.path))
    assertEquals(expectedFileName, file.name)

    val expectedContent =
      if (expectedPackage.isNotEmpty()) {
        """
              package $expectedPackage
              
              class $expectedClassName
        """.trimIndent()
      }
      else {
        "class $expectedClassName"
      }
    assertEquals(expectedContent, file.text)
  }
}