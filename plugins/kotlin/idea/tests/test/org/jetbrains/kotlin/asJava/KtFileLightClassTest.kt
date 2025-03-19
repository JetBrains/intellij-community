// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.asJava

import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/asJava/fileLightClass")
@RunWith(JUnit38ClassRunner::class)
class KtFileLightClassTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return if (name.contains("KotlinSDK")) {
            object : KotlinLightProjectDescriptor() {
                override fun getSdk(): Sdk? {
                    return IdeaTestUtil.createMockJdk("KotlinSDK", KotlinLightProjectDescriptor.INSTANCE.sdk?.homePath!!) {
                        it.addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlib), OrderRootType.CLASSES)
                    }
                }
            }
        } else {
            KotlinLightProjectDescriptor.INSTANCE
        }
    }

    fun testSimple() {
        val file = myFixture.configureByText("A.kt", "class C {}\nobject O {}") as KtFile
        val classes = file.classes
        assertEquals(2, classes.size)
        assertEquals("C", classes[0].qualifiedName)
        assertEquals("O", classes[1].qualifiedName)
    }

    fun testFileClass() {
        val file = myFixture.configureByText("A.kt", "fun f() {}") as KtFile
        val classes = file.classes
        assertEquals(1, classes.size)
        assertEquals("AKt", classes[0].qualifiedName)
    }

    fun testMultifileClass() {
        val file = myFixture.configureByFiles("multifile1.kt", "multifile2.kt")[0] as KtFile
        val aClass = file.classes.single()
        assertEquals(1, aClass.findMethodsByName("foo", false).size)
        assertEquals(1, aClass.findMethodsByName("bar", false).size)
    }

    fun testAliasesOnly() {
        val file = myFixture.configureByFile("aliasesOnly.kt") as KtFile
        val aClass = file.classes.single()
        assertEquals(0, aClass.methods.size)
    }

    fun testNoFacadeForScript() {
        val file = myFixture.configureByText("foo.kts", "package foo") as KtFile
        assertEquals(0, file.classes.size)
        val javaSupport = KotlinAsJavaSupport.getInstance(project)
        assertNull(javaSupport.getLightFacade(file))

        val fqName = FqName("foo.FooKt")
        val scope = GlobalSearchScope.allScope(project)
        val facadeClasses = javaSupport.getFacadeClasses(fqName, scope)
        assertEquals(0, facadeClasses.size)

        val facadeFiles = javaSupport.findFilesForFacade(fqName, scope)
        assertEquals(0, facadeFiles.size)
    }

    fun testFacadeClassInStdLib() {
        ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
        val fqName = FqName("kotlin.collections.CollectionsKt")
        val scope = GlobalSearchScope.allScope(project)
        val javaSupport = KotlinAsJavaSupport.getInstance(project)
        val facadeClass = javaSupport.getFacadeClasses(fqName, scope).singleOrNull()
        assertNotNull(facadeClass)
    }

    fun testFacadeClassInKotlinSDK() {
        val fqName = FqName("kotlin.collections.CollectionsKt")
        val scope = GlobalSearchScope.allScope(project)
        val javaSupport = KotlinAsJavaSupport.getInstance(project)
        val facadeClass = javaSupport.getFacadeClasses(fqName, scope).singleOrNull()
        assertNotNull(facadeClass)
    }

    fun testNoFacadeForExpectClass() {
        val file = myFixture.configureByText("foo.kt", "expect fun foo(): Int") as KtFile
        assertEquals(0, file.classes.size)
        val javaSupport = KotlinAsJavaSupport.getInstance(project)
        assertNull(javaSupport.getLightFacade(file))

        val fqName = FqName("foo.FooKt")
        val scope = GlobalSearchScope.allScope(project)
        val facadeClasses = javaSupport.getFacadeClasses(fqName, scope)
        assertEquals(0, facadeClasses.size)

        val facadeFiles = javaSupport.getFacadeClasses(fqName, scope)
        assertEquals(0, facadeFiles.size)
    }

    fun testInjectedCode() {
        myFixture.configureByText(
            "foo.kt", """
            import org.intellij.lang.annotations.Language

            fun foo(@Language("kotlin") a: String){a.toString()}

            fun bar(){ foo("class<caret> A") }
            """
        )


        myFixture.testHighlighting("foo.kt")

        val injectedFile = (editor as? EditorWindow)?.injectedFile
        assertEquals("Wrong injection language", "kotlin", injectedFile?.language?.id)
        assertEquals("Injected class should be `A`", "A", ((injectedFile as KtFile).declarations.single() as KtClass).toLightClass()!!.name)
    }

    fun testPropertyWithPrivateSetter() {
        val file = myFixture.configureByFile("propWithPrivateSetter.kt") as KtFile
        val aClass = file.classes.single()
        val methods = aClass.methods
        val methodNames = methods.map(PsiMethod::getName)
        assertEquals(methodNames.toString(), 3, methods.size)
        assertTrue(methods.toString(), "getProp" in methodNames)
        assertTrue(methods.toString(), "setProp" in methodNames)
    }

    fun testSameVirtualFileForLightElement() {
        val psiFile = myFixture.addFileToProject(
            "pkg/AnnotatedClass.kt", """
            package pkg

            class AnnotatedClass {
                    @Deprecated("a")
                    fun bar(param: String) = null
            }
        """.trimIndent()
        )

        fun lightElement(file: PsiFile): PsiElement = (file as KtFile).classes.single()
            .methods.first { it.name == "bar" }
            .annotations.first { it.qualifiedName == "kotlin.Deprecated" }.also {
                // Otherwise following asserts have no sense
                assertTrue("psi element should be light ", it is KtLightElement<*, *>)
            }


        val copied = psiFile.copied()
        assertNull("virtual file for copy should be null", copied.virtualFile)
        assertNotNull("psi element in copy", lightElement(copied))
        assertSame("copy.originalFile should be psiFile", copied.originalFile, psiFile)

        // virtual files should be the same for light and non-light element,
        // otherwise we will not be able to find proper module by file from light element
        assertSame(
            "virtualFiles of element and file itself should be the same",
            lightElement(copied).containingFile.originalFile.virtualFile,
            copied.originalFile.virtualFile
        )
    }

}
