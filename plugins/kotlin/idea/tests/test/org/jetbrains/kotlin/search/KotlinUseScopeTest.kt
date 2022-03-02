// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.search

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.search.useScope
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import kotlin.test.assertNotEquals

class KotlinUseScopeTest : JavaCodeInsightFixtureTestCase() {
    fun `test all`() {
        val moduleA = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "ModuleA",
            myFixture.tempDirFixture.findOrCreateDir("ModuleA"),
        )

        myFixture.addFileToProject(
            "ModuleA/one/PrivateK.kt",
            """
                package one
                private class PrivateK {
                  private object PrivateNestedObject {
                    object NestedObjectInPrivate {}
                  }
                  private var privateMemberPropertyFromPrivateClass = 42
                  private fun privateMemberFunctionFromPrivateClass() = 42
                  var publicMemberPropertyFromPrivateClass = 42
                  fun publicMemberFunctionFromPrivateClass() = 42
                }
                private object PrivateObject {}
                private fun privateFunction() = Unit
                private var privateProperty = 42
                fun publicFunction() = Unit
                var publicProperty = 42
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "ModuleA/one/JavaClass.java",
            """
                package one;
                public class JavaClass {
    
                }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "ModuleA/two/OtherJavaClass.java",
            """
                package two;
                public class OtherJavaClass {
                }
            """.trimIndent()
        )

        myFixture.addFileToProject("ModuleA/one/KK.kt", "package one\nclass KK{\nclass Nested\n}")
        myFixture.addFileToProject("ModuleA/two/I.kt", "package two\ninterface I")

        val moduleB = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            "ModuleB",
            myFixture.tempDirFixture.findOrCreateDir("ModuleB"),
        )

        myFixture.addFileToProject(
            "ModuleB/one/I.kt",
            """
                package one
                interface I {
                  class Nested {
                    private fun privateMemberFunctionFromNested() = 42
                    fun publicMemberFunctionFromNested() = 42
                    private var privateMemberPropertyFromNested = 42
                    var publicMemberPropertyFromNested = 42
                  }
                }
                fun publicFunction2() {}
            """.trimIndent(),
        )

        myFixture.addFileToProject(
            "ModuleB/two/PrivateInterface.kt",
            "package two\nprivate interface PrivateInterface\nprivate var privateProperty = 42\nvar publicProperty = 42"
        )

        myFixture.addFileToProject("ModuleB/one/JavaModB.java", "package one;\npublic class JavaModB {}")
        myFixture.addFileToProject("ModuleB/two/JavaModBTwo.java", "package one;\npublic class JavaModBTwo {}")
        ModuleRootModificationUtil.addDependency(moduleB, moduleA)

        val moduleAScope = """
            global:
            A/one/JavaClass.java,
            A/one/KK.kt,
            A/one/PrivateK.kt,
            A/two/I.kt,
            A/two/OtherJavaClass.java,
            B/one/I.kt,
            B/one/JavaModB.java,
            B/two/JavaModBTwo.java,
            B/two/PrivateInterface.kt,
        """.trimIndent()

        assertLightAndOriginalScope(findClass("one.KK"), moduleAScope)
        assertLightAndOriginalScope(findClass("one.KK.Nested"), moduleAScope)
        assertLightAndOriginalScope(findClass("two.I"), moduleAScope)
        assertLightAndOriginalScope(findMethod("one.PrivateKKt", "publicFunction"), moduleAScope)
        assertLightAndOriginalScope(findMethod("one.PrivateKKt", "getPublicProperty"), moduleAScope)
        assertLightAndOriginalScope(findMethod("one.PrivateKKt", "setPublicProperty"), moduleAScope)

        val moduleBScope = """
            global:
            B/one/I.kt,
            B/one/JavaModB.java,
            B/two/JavaModBTwo.java,
            B/two/PrivateInterface.kt,
        """.trimIndent()

        assertNotEquals(moduleBScope, moduleAScope)
        assertLightAndOriginalScope(findClass("one.I"), moduleBScope)
        assertLightAndOriginalScope(findClass("one.I.Nested"), moduleBScope)
        assertLightAndOriginalScope(findMethod("one.IKt", "publicFunction2"), moduleBScope)
        assertLightAndOriginalScope(findMethod("two.PrivateInterfaceKt", "getPublicProperty"), moduleBScope)
        assertLightAndOriginalScope(findMethod("two.PrivateInterfaceKt", "setPublicProperty"), moduleBScope)
        assertLightAndOriginalScope(findMethod("one.I.Nested", "getPublicMemberPropertyFromNested"), moduleBScope)
        assertLightAndOriginalScope(findMethod("one.I.Nested", "setPublicMemberPropertyFromNested"), moduleBScope)
        assertLightAndOriginalScope(findMethod("one.I.Nested", "publicMemberFunctionFromNested"), moduleBScope)

        val privateJvmModuleAScope = """
            global:
            A/one/JavaClass.java,
            A/one/PrivateK.kt,
        """.trimIndent()

        assertNotEquals(moduleAScope, privateJvmModuleAScope)
        assertLightAndOriginalScope(findClass("one.PrivateK"), privateJvmModuleAScope)
        assertLightAndOriginalScope(findClass("one.PrivateObject"), privateJvmModuleAScope)

        val restrictedByPrivateKClass = """
            local:
            files:
            A/one/PrivateK.kt,
            elements:
            PrivateK,
        """.trimIndent()

        assertLightAndOriginalScope(
            findClass("one.PrivateK.PrivateNestedObject"),
            restrictedByPrivateKClass,
        )

        assertLightAndOriginalScope(
            findClass("one.PrivateK.PrivateNestedObject.NestedObjectInPrivate"),
            restrictedByPrivateKClass,
        )

        val restrictedLocalByPrivateKFile = """
            local:
            files:
            A/one/PrivateK.kt,
            elements:
            PrivateK.kt,
        """.trimIndent()

        assertLightAndOriginalScope(
            findMethod("one.PrivateKKt", "privateFunction"),
            restrictedLocalByPrivateKFile,
        )

        val restrictedByPrivateKFile = """
            global:
            A/one/PrivateK.kt,
        """.trimIndent()

        assertLightAndOriginalScope(
            findField("one.PrivateKKt", "privateProperty"),
            restrictedByPrivateKFile,
            expectedOriginal = restrictedLocalByPrivateKFile,
        )

        assertLightAndOriginalScope(
            findMethod("one.PrivateK", "getPublicMemberPropertyFromPrivateClass"),
            privateJvmModuleAScope,
        )

        assertLightAndOriginalScope(
            findMethod("one.PrivateK", "setPublicMemberPropertyFromPrivateClass"),
            privateJvmModuleAScope,
        )

        assertLightAndOriginalScope(
            findField("one.PrivateKKt", "publicProperty"),
            restrictedByPrivateKFile,
            expectedOriginal = moduleAScope,
        )

        assertLightAndOriginalScope(
            findField("one.PrivateK", "privateMemberPropertyFromPrivateClass"),
            restrictedByPrivateKFile,
            expectedOriginal = restrictedByPrivateKClass,
        )

        assertLightAndOriginalScope(
            findField("one.PrivateK", "publicMemberPropertyFromPrivateClass"),
            restrictedByPrivateKFile,
            expectedOriginal = privateJvmModuleAScope,
        )

        assertLightAndOriginalScope(
            findMethod("one.PrivateK", "publicMemberFunctionFromPrivateClass"),
            privateJvmModuleAScope,
        )

        assertLightAndOriginalScope(
            findMethod("one.PrivateK", "privateMemberFunctionFromPrivateClass"),
            restrictedByPrivateKClass,
        )

        val privateJvmModuleBScope = """
            global:
            B/two/JavaModBTwo.java,
            B/two/PrivateInterface.kt,
        """.trimIndent()

        assertNotEquals(moduleBScope, privateJvmModuleBScope)
        assertLightAndOriginalScope(findClass("two.PrivateInterface"), privateJvmModuleBScope)

        val restrictedByPrivateInterfaceFile = """
            global:
            B/two/PrivateInterface.kt,
        """.trimIndent()

        assertLightAndOriginalScope(
            findField("two.PrivateInterfaceKt", "privateProperty"),
            restrictedByPrivateInterfaceFile,
            expectedOriginal = """
                local:
                files:
                B/two/PrivateInterface.kt,
                elements:
                PrivateInterface.kt,
            """.trimIndent(),
        )

        assertLightAndOriginalScope(
            findField("two.PrivateInterfaceKt", "publicProperty"),
            restrictedByPrivateInterfaceFile,
            expectedOriginal = moduleBScope,
        )

        val restrictedByIFile = """
            global:
            B/one/I.kt,
        """.trimIndent()

        assertLightAndOriginalScope(
            findField("one.I.Nested", "publicMemberPropertyFromNested"),
            restrictedByIFile,
            expectedOriginal = moduleBScope,
        )

        val restrictedLocalByNestedClass = """
            local:
            files:
            B/one/I.kt,
            elements:
            Nested,
        """.trimIndent()

        assertLightAndOriginalScope(
            findField("one.I.Nested", "privateMemberPropertyFromNested"),
            restrictedByIFile,
            expectedOriginal = restrictedLocalByNestedClass
        )

        assertLightAndOriginalScope(
            findMethod("one.I.Nested", "privateMemberFunctionFromNested"),
            restrictedLocalByNestedClass,
        )
    }

    private fun findClass(qualifier: String): PsiClass = myFixture.findClass(qualifier)
    private fun findMethod(qualifier: String, name: String): PsiMethod =
        myFixture.findClass(qualifier).findMethodsByName(name, false).single()

    private fun findField(qualifier: String, name: String): PsiField =
        myFixture.findClass(qualifier).findFieldByName(name, false)!!

}

private fun assertLightAndOriginalScope(element: PsiNamedElement, expected: String, expectedOriginal: String = expected) {
    val ktElement = element.unwrapped as KtNamedDeclaration
    TestCase.assertEquals("light: ${element.getKotlinFqName().toString()}", expected, element.useScope().findFiles())
    TestCase.assertEquals("kt: ${ktElement.fqName.toString()}", expectedOriginal, ktElement.useScope().findFiles())
}

private fun SearchScope.findFiles() = findFiles { vFile -> vFile.path.substringAfterLast("Module") }