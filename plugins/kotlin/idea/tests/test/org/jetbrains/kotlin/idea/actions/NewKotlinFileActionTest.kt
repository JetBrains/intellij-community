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
        userInput = "MyClass",
        expectedDirPath = "",
        expectedFileName = "MyClass.kt",
        expectedPackage = "",
        expectedClassName = "MyClass"
    )

    fun testClassNameAndExtension() = doTest(
        userInput = "Foo.kt",
        expectedDirPath = "",
        expectedFileName = "Foo.kt",
        expectedPackage = "",
        expectedClassName = "Foo"
    )

    fun testPackageAndClassName() = doTest(
        userInput = "com.my.MyBar",
        expectedDirPath = "/com/my",
        expectedFileName = "MyBar.kt",
        expectedPackage = "com.my",
        expectedClassName = "MyBar"
    )

    fun testClassNameAndSuffix() = doTest(
        userInput = "MyClass.suffix",
        expectedDirPath = "",
        expectedFileName = "MyClass.suffix.kt",
        expectedPackage = "",
        expectedClassName = "MyClass"
    )

    fun testPackageAndClassNameAndSuffix() = doTest(
        userInput = "com.my.MyClass.suffix",
        expectedDirPath = "/com/my",
        expectedFileName = "MyClass.suffix.kt",
        expectedPackage = "com.my",
        expectedClassName = "MyClass"
    )

    fun testPackageAndClassNameAndSuffixAndExtension() = doTest(
        userInput = "com.my.MyClass.suffix.kt",
        expectedDirPath = "/com/my",
        expectedFileName = "MyClass.suffix.kt",
        expectedPackage = "com.my",
        expectedClassName = "MyClass"
    )

    fun testPathAndClassName() = doTest(
        userInput = "com/my/MyClass",
        expectedDirPath = "/com/my",
        expectedFileName = "MyClass.kt",
        expectedPackage = "com.my",
        expectedClassName = "MyClass"
    )

    fun testPathAndClassNameAndSuffixAndExtension() = doTest(
        userInput = "com/my/MyClass.suffix.kt",
        expectedDirPath = "/com/my",
        expectedFileName = "MyClass.suffix.kt",
        expectedPackage = "com.my",
        expectedClassName = "MyClass"
    )

    fun testUppercasePackageAndClassNameAndThreeSuffixes() = doTest(
        userInput = "com.my.Pack.foo.MyClass.suffix1.suffix2.suffix3",
        expectedDirPath = "/com/my/Pack/foo",
        expectedFileName = "MyClass.suffix1.suffix2.suffix3.kt",
        expectedPackage = "com.my.Pack.foo",
        expectedClassName = "MyClass"
    )

    fun testUppercasePackageAndLowercaseClassNameWhenDirExists() = doTest(
        userInput = "com.My.Package.bar.myclass",
        existentPath = "/com/My/Package",
        expectedDirPath = "/com/My/Package/bar",
        expectedFileName = "myclass.kt",
        expectedPackage = "com.My.Package.bar",
        expectedClassName = "myclass"
    )

    fun testUppercasePackageAndClassNameAndSuffixWhenDirExists() = doTest(
        userInput = "com.My.Foo.bar.MyClass.suffix",
        existentPath = "/com/My/Package",
        expectedDirPath = "/com/My/Foo/bar",
        expectedFileName = "MyClass.suffix.kt",
        expectedPackage = "com.My.Foo.bar",
        expectedClassName = "MyClass"
    )

    private fun doTest(
        userInput: String,
        existentPath: String? = null,
        expectedDirPath: String,
        expectedFileName: String,
        expectedPackage: String,
        expectedClassName: String
    ) {
        if (existentPath != null) {
            myFixture.tempDirFixture.findOrCreateDir(existentPath)
        }

        val actDir = myFixture.psiManager.findDirectory(myFixture.tempDirFixture.findOrCreateDir("."))!!
        val file = createKotlinFileFromTemplate(userInput, testTemplate, actDir)!!

        assertEquals(expectedDirPath, file.virtualFile.parent.path.removePrefix(actDir.virtualFile.path))
        assertEquals(expectedFileName, file.name)

        val expectedContent =
            if (expectedPackage.isNotEmpty()) {
                """
                      package $expectedPackage
                      
                      class $expectedClassName
                """.trimIndent()
            } else {
                "class $expectedClassName"
            }
        assertEquals(expectedContent, file.text)
        assertEquals("Create action should not modify predefined template", "", testTemplate.fileName)
    }
}