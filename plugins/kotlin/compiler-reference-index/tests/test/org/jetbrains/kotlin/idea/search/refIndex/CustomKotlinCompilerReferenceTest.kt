// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.CompilerReferenceService
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.util.Function
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.highlighter.markers.OVERRIDDEN_FUNCTION
import org.jetbrains.kotlin.idea.highlighter.markers.SUBCLASSED_CLASS
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

@SkipSlowTestLocally
open class CustomKotlinCompilerReferenceTest6 : KotlinCompilerReferenceTestBase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun getTestDataPath(): String = KotlinRoot.DIR
        .resolve("compiler-reference-index/tests/testData/")
        .resolve("customCompilerIndexData")
        .path + "/"

    override fun setUp() {
        super.setUp()
        installCompiler()
        myFixture.testDataPath = testDataPath + name
    }

    private fun assertIndexUnavailable() = assertNull(getReferentFilesForElementUnderCaret())
    private fun assertUsageInMainFile() = assertEquals(setOf("Main.kt"), getReferentFilesForElementUnderCaret())
    private fun addFileAndAssertIndexNotReady(fileName: String = "Another.kt") {
        myFixture.addFileToProject(fileName, "")
        assertIndexUnavailable()
    }

    fun `test match testData with tests`() {
        if (!isCompatibleVersions) return

        val testNames = CustomKotlinCompilerReferenceTest6::class.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.map(KFunction<*>::name).toSet()
        for (testDirectory in Path(testDataPath).listDirectoryEntries()) {
            if (!testDirectory.isDirectory() || testDirectory.listDirectoryEntries().isEmpty()) continue

            val testDirectoryName = testDirectory.name
            assertTrue("Test not found for '$testDirectoryName' directory", testDirectoryName in testNames)
        }
    }

    fun testIsNotReady() {
        if (!isCompatibleVersions) return

        myFixture.configureByFile("Main.kt")
        assertIndexUnavailable()
    }

    fun testFindItself() {
        if (!isCompatibleVersions) return

        myFixture.configureByFile("Main.kt")
        rebuildProject()
        assertUsageInMainFile()

        addFileAndAssertIndexNotReady()

        rebuildProject()
        assertUsageInMainFile()

        myFixture.addFileToProject("Another.groovy", "")
        assertUsageInMainFile()

        addFileAndAssertIndexNotReady("JavaClass.java")
    }

    fun testSimpleJavaLibraryClass() {
        if (!isCompatibleVersions) return

        myFixture.configureByFiles("Main.kt", "Boo.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("Main.kt"), getReferentFiles(myFixture.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST), true))
    }

    fun testHierarchyJavaLibraryClass() {
        if (!isCompatibleVersions) return

        myFixture.configureByFiles("Main.kt", "Boo.kt", "Doo.kt", "Foo.kt")
        rebuildProject()
        TestCase.assertEquals(setOf("Foo.kt"), getReferentFiles(myFixture.findClass("java.util.AbstractList"), true))
        myFixture.addFileToProject("Another.kt", "")
        TestCase.assertEquals(setOf("Foo.kt"), getReferentFiles(myFixture.findClass("java.util.AbstractList"), true))
    }

    fun testTopLevelConstantWithJvmName() {
        if (!isCompatibleVersions) return

        myFixture.configureByFiles("Main.kt", "Bar.kt", "Foo.kt", "Doo.kt", "Empty.java", "JavaRead.java")
        // JvmName for constants isn't supported
        assertThrows(AssertionFailedError::class.java) { rebuildProject() }
    }

    fun `test sub and super types`() {
        if (!isCompatibleVersions) return

        myFixture.addFileToProject(
            "one/two/Main.kt",
            """
                package one.two
                open class K
                open class KK : K()
                open class KK2 : K()
                open class KKK : KK()
                open class KKK2 : KK()
            """.trimIndent()
        )

        val mainClassName = "one.two.K"
        val deepSubtypes = findClassSubtypes(mainClassName, true)
        val subtypes = findClassSubtypes(mainClassName, false)

        assertEquals(listOf("one.two.KK", "one.two.KK2"), subtypes)
        assertEquals(listOf("one.two.KK", "one.two.KK2", "one.two.KKK", "one.two.KKK2"), deepSubtypes)

        rebuildProject()
        forEachBoolean { deep ->
            //assertEquals(emptyList<String>(), findSubOrSuperTypes("one.two.K", deep, subtypes = false))
            //assertEquals(emptyList<String>(), findSubOrSuperTypes("Another", deep, subtypes = false))

            assertEquals(emptyList<String>(), findSubOrSuperTypes("one.two.KKK", deep, subtypes = true))
            assertEquals(emptyList<String>(), findSubOrSuperTypes("Another", deep, subtypes = true))
        }

        assertEquals(subtypes, findSubOrSuperTypes("one.two.K", deep = false, subtypes = true))
        assertEquals(deepSubtypes, findSubOrSuperTypes("one.two.K", deep = true, subtypes = true))

        //assertEquals(listOf("one.two.KK"), findSubOrSuperTypes("one.two.KKK", deep = false, subtypes = false))
        //assertEquals(listOf("one.two.K", "one.two.KK"), findSubOrSuperTypes("one.two.KKK", deep = true, subtypes = false))
    }

    open fun testMixedSubtypes() {
        if (!isCompatibleVersions) return

        myFixture.configureByFiles("one/two/MainJava.java", "one/two/SubMainJavaClass.java", "one/two/KotlinSubMain.kt")
        val className = "one.two.MainJava"
        val subtypes = findClassSubtypes(className, true)
        assertEquals(
            listOf(
                "main.another.one.two.K",
                "main.another.one.two.KotlinFromAlias",
                "main.another.one.two.KotlinMain",
                "main.another.one.two.KotlinMain.NestedKotlinMain",
                "main.another.one.two.KotlinMain.NestedKotlinNestedMain",
                "main.another.one.two.KotlinMain.NestedKotlinSubMain",
                "main.another.one.two.ObjectKotlin",
                "main.another.one.two.ObjectKotlin.NestedObjectKotlin.NestedNestedKotlin",
                "one.two.MainJava.InnerJava",
                "one.two.MainJava.InnerJava2",
                "one.two.MainJava.NestedJava",
                "one.two.MainJava.Wrapper.NestedWrapper",
                "one.two.SubMainJavaClass",
                "one.two.SubMainJavaClass.SubInnerClass",
                "one.two.SubMainJavaClass.SubNestedJava"
            ),
            subtypes,
        )

        rebuildProject()
        assertEquals(
            subtypes,
            findHierarchy(myFixture.findClass(className)),
        )
    }

    fun testObjects() {
        if (!isCompatibleVersions) return

        myFixture.configureByFiles(
            "CompanionInstanceUsage.java",
            "CompanionUsage.java",
            "KotlinClass.kt",
            "KotlinClassWithNamedCompanion.kt",
            "MyObject.kt",
            "NamedCompanionInstanceUsage.java",
            "NamedCompanionUsage.java",
            "NestedObjectInstanceUsage.java",
            "NestedObjectUsage.java",
            "ObjectInstanceUsage.java",
            "ObjectUsage.java",
            "SecondUsage.java",
        )

        rebuildProject()
        doObjectUsagesTest(
            objectName = "MyObject",
            classFiles = listOf(
                "NestedObjectInstanceUsage.java",
                "NestedObjectUsage.java",
                "ObjectInstanceUsage.java",
                "ObjectUsage.java",
                "SecondUsage.java",
            ),
            instanceFiles = listOf(
                "ObjectInstanceUsage.java",
                "SecondUsage.java",
            ),
        )

        doObjectUsagesTest(
            objectName = "MyObject.Nested",
            classFiles = listOf(
                "NestedObjectInstanceUsage.java",
                "NestedObjectUsage.java",
            ),
            instanceFiles = listOf(
                "NestedObjectInstanceUsage.java",
            ),
        )

        doObjectUsagesTest(
            objectName = "KotlinClass.Companion",
            classFiles = listOf(
                "CompanionUsage.java",
            ),
            instanceFiles = listOf(
                "CompanionInstanceUsage.java",
            ),
        )

        doObjectUsagesTest(
            objectName = "KotlinClassWithNamedCompanion.Named",
            classFiles = listOf(
                "NamedCompanionUsage.java",
            ),
            instanceFiles = listOf(
                "NamedCompanionInstanceUsage.java",
            ),
        )
    }

    private fun doObjectUsagesTest(objectName: String, classFiles: List<String>, instanceFiles: List<String>) {
        val javaService = CompilerReferenceService.getInstance(project) as CompilerReferenceServiceBase<*>
        val myObject = myFixture.findClass(objectName)
        val compilerRefs = javaService.getCompilerRefsForTests(myObject) ?: error("$objectName: compiler refs is not found")
        assertEquals(objectName, 2, compilerRefs.size)
        val classRef = compilerRefs.firstIsInstanceOrNull<CompilerRef.CompilerClassHierarchyElementDef>()
            ?: error("$objectName: class ref is not found")

        val instanceRef = compilerRefs.firstIsInstanceOrNull<CompilerRef.CompilerMember>()
            ?: error("$objectName: instance ref is not found")

        assertCompilerRefs(
            "$objectName class usages",
            javaService,
            classRef,
            classFiles,
        )

        assertCompilerRefs(
            "$objectName instance usages",
            javaService,
            instanceRef,
            instanceFiles,
        )
    }

    private fun assertCompilerRefs(
        errorMessage: String,
        service: CompilerReferenceServiceBase<*>,
        compilerRef: CompilerRef,
        files: List<String>,
    ) {
        assertEquals(
            errorMessage,
            files,
            service.getReferentFilesForTests(compilerRef, true)?.map(VirtualFile::getName)?.sorted().orEmpty(),
        )
    }

    open fun testTooltips() {
        if (!isCompatibleVersions) return

        doTestTooltips(SUBCLASSED_CLASS.tooltip, OVERRIDDEN_FUNCTION.tooltip)
    }

    protected fun doTestTooltips(
        subclassTooltip: Function<in PsiElement, String>,
        overriddenFunctionTooltip: Function<in PsiElement, String>,
    ) {
        myFixture.configureByFiles(
            "anonObject.kt",
            "JavaClass.java",
            "JavaJavaSub.java",
            "JavaSub.java",
            "KInterface.kt",
            "NestedSub.kt",
            "Sub.kt",
            "SubJavaJavaSub.kt",
            "SubJavaSub.kt",
            "SubObject.kt",
            "SubSub.kt",
            "SubSubSub.kt",
        )

        val kInterface = myFixture.findClass("KInterface")

        val kInterfaceMethod = kInterface.findMethodsByName("foo", false).single()

        testTooltips(
            subclassTooltip to kInterface,
            overriddenFunctionTooltip to kInterfaceMethod,
        )
    }

    private fun testTooltips(vararg tooltips: Pair<Function<in PsiElement, String>, PsiElement>) {
        val expected = tooltips.map { it.first.`fun`(it.second) }
        rebuildProject()
        tooltips.zip(expected).forEach { (pair, before) ->
            val marker = pair.first
            val element = pair.second
            assertEquals(marker.toString(), before, marker.`fun`(element))
        }
    }

    private fun findClassSubtypes(className: String, deep: Boolean) = ClassInheritorsSearch.search(myFixture.findClass(className), deep)
        .findAll()
        .map { it.kotlinFqName.toString() }
        .sorted()

    fun testNonPresentedClass(): Unit = doTestNonPresentedClass(7)

    fun testNonPresentedClassWithCompanion(): Unit = doTestNonPresentedClass(13)

    private fun doTestNonPresentedClass(declarationsCount: Int) {
        if (!isCompatibleVersions) return

        myFixture.configureByFiles(
            "Hierarchy.java",
            "KotlinOnlyClass.kt",
            "Parameter.java",
        )

        val kotlinOnlyClass = myFixture.findClass("one.KotlinOnlyClass").unwrapped as KtClass
        val declarations = kotlinOnlyClass.declarations.fold(mutableListOf<KtDeclaration>()) { list, declaration ->
            if (declaration is KtObjectDeclaration) list += declaration.declarations else list += declaration
            list
        }

        assertEquals(declarationsCount, declarations.size)
        rebuildProject()
        for (declaration in declarations) {
            val referentFiles = getReferentFiles(declaration, withJavaIndex = true) ?: error("${declaration.name}: file is not found")
            assertTrue(declaration.name, "Hierarchy.java" in referentFiles)
            assertTrue(declaration.name, "Parameter.java" in referentFiles)
        }
    }
}
