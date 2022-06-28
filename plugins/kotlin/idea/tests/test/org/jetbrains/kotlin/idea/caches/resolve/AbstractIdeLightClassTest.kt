// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.*
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinDaemonAnalyzerTestCase
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.asJava.PsiClassRenderer
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import kotlin.test.assertNotNull

abstract class AbstractIdeLightClassTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTest(@Suppress("UNUSED_PARAMETER") unused: String) {
        val fileName = fileName()
        val extraFilePath = when {
            fileName.endsWith(fileExtension) -> fileName.replace(fileExtension, ".extra$fileExtension")
            else -> error("Invalid test data extension")
        }

        val fileText = File(testDataPath, fileName).readText()
        withCustomCompilerOptions(fileText, project, module) {
            val testFiles = if (File(testDataPath, extraFilePath).isFile) listOf(fileName, extraFilePath) else listOf(fileName)
            myFixture.configureByFiles(*testFiles.toTypedArray())
            if ((myFixture.file as? KtFile)?.isScript() == true) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
            }

            val ktFile = myFixture.file as KtFile
            val testData = dataFile()
            testLightClass(
                KotlinTestUtils.replaceExtension(testData, "java"),
                testData,
                { LightClassTestCommon.removeEmptyDefaultImpls(it) },
                { fqName ->
                    findClass(fqName, ktFile, project)?.apply {
                        checkConsistency(this as KtLightClass)
                        PsiElementChecker.checkPsiElementStructure(this)
                    }
                },
            )
        }
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_WITH_STDLIB_JDK8

    open val fileExtension = ".kt"
}

abstract class AbstractIdeCompiledLightClassTest : KotlinDaemonAnalyzerTestCase() {
    override fun setUp() {
        super.setUp()

        val testName = getTestName(false)

        val testDataDir = TestMetadataUtil.getTestData(this::class.java)
        val testFile = listOf(File(testDataDir, "$testName.kt"), File(testDataDir, "$testName.kts")).first { it.exists() }

        val extraClasspath = mutableListOf(KotlinArtifacts.instance.jetbrainsAnnotations, KotlinArtifacts.instance.kotlinStdlibJdk8)
        if (testFile.extension == "kts") {
            extraClasspath += KotlinArtifacts.instance.kotlinScriptRuntime
        }

        val extraOptions = KotlinTestUtils.parseDirectives(testFile.readText())[
                CompilerTestDirectives.JVM_TARGET_DIRECTIVE.substringBefore(":")
        ]?.let { jvmTarget ->
            listOf("-jvm-target", jvmTarget)
        } ?: emptyList()

        val libraryJar = KotlinCompilerStandalone(
            listOf(testFile),
            classpath = extraClasspath,
            options = extraOptions
        ).compile()

        val jarUrl = "jar://" + FileUtilRt.toSystemIndependentName(libraryJar.absolutePath) + "!/"
        ModuleRootModificationUtil.addModuleLibrary(module, jarUrl)
    }

    fun doTest(testDataPath: String) {
        val testDataFile = File(testDataPath)
        val expectedFile = KotlinTestUtils.replaceExtension(testDataFile, "compiled.java").let {
            if (it.exists()) it else KotlinTestUtils.replaceExtension(testDataFile, "java")
        }
        withCustomCompilerOptions(testDataFile.readText(), project, module) {
            testLightClass(
                expectedFile,
                testDataFile,
                { it },
                {
                    findClass(it, null, project)?.apply {
                        PsiElementChecker.checkPsiElementStructure(this)
                    }
                },
                MembersFilterForCompiledClasses
            )
        }
    }

    private object MembersFilterForCompiledClasses : PsiClassRenderer.MembersFilter {
        override fun includeMethod(psiMethod: PsiMethod): Boolean {
            // Exclude methods for local functions.
            // JVM_IR generates local functions (and some lambdas) as private methods in the surrounding class.
            // Such methods are private and have names such as 'foo$...'.
            // They are not a part of the public API, and are not represented in the light classes.
            // NB this is a heuristic, and it will obviously fail for declarations such as 'private fun `foo$bar`() {}'.
            // However, it allows writing code in more or less "idiomatic" style in the light class tests
            // without thinking about private ABI and compiler optimizations.
            if (psiMethod.modifierList.hasExplicitModifier(PsiModifier.PRIVATE)) {
                return '$' !in psiMethod.name
            }
            return super.includeMethod(psiMethod)
        }
    }
}

private fun testLightClass(
    expected: File,
    testData: File,
    normalize: (String) -> String,
    findLightClass: (String) -> PsiClass?,
    membersFilter: PsiClassRenderer.MembersFilter = PsiClassRenderer.MembersFilter.DEFAULT
) {
    val actual = LightClassTestCommon.getActualLightClassText(
        testData,
        findLightClass = findLightClass,
        normalizeText = { text ->
            //NOTE: ide and compiler differ in names generated for parameters with unspecified names
            text.replace("java.lang.String s,", "java.lang.String p,")
                .replace("java.lang.String s)", "java.lang.String p)")
                .replace("java.lang.String s1", "java.lang.String p1")
                .replace("java.lang.String s2", "java.lang.String p2")
                .replace("java.lang.Object o)", "java.lang.Object p)")
                .replace("java.lang.String[] strings", "java.lang.String[] p")
                .removeLinesStartingWith("@" + JvmAnnotationNames.METADATA_FQ_NAME.asString())
                .run(normalize)
        },
        membersFilter = membersFilter
    )
    KotlinTestUtils.assertEqualsToFile(expected, actual)
}

fun findClass(fqName: String, ktFile: KtFile?, project: Project): PsiClass? {
    ktFile?.script?.let {
        return it.toLightClass()
    }

    return JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.allScope(project))
        ?: PsiTreeUtil.findChildrenOfType(ktFile, KtClassOrObject::class.java)
            .find { fqName.endsWith(it.nameAsName!!.asString()) }
            ?.toLightClass()
}

private fun checkConsistency(lightClass: KtLightClass) {
    checkModifierList(lightClass.modifierList!!)

    for (field in lightClass.fields) {
        field as KtLightField
        checkModifierList(field.modifierList!!)
        checkAnnotationConsistency(field)
    }

    for (method in lightClass.methods) {
        method as KtLightMethod
        checkModifierList(method.modifierList)
        checkAnnotationConsistency(method)
        method.parameterList.parameters.forEach {
            checkAnnotationConsistency(it as KtLightParameter)
        }
    }

    checkAnnotationConsistency(lightClass)

    lightClass.innerClasses.forEach { checkConsistency(it as KtLightClass) }
}

private fun checkAnnotationConsistency(modifierListOwner: KtLightElement<*, PsiModifierListOwner>) {
    if (modifierListOwner is KtLightClassForFacade) return

    val annotations = modifierListOwner.safeAs<PsiModifierListOwner>()?.modifierList?.annotations ?: return
    for (annotation in annotations) {
        if (annotation !is KtLightNullabilityAnnotation<*>)
            assertNotNull(
                annotation!!.nameReferenceElement,
                "nameReferenceElement should be not null for $annotation of ${annotation.javaClass}"
            )
    }
}

private fun checkModifierList(modifierList: PsiModifierList) {
    // see org.jetbrains.kotlin.asJava.elements.KtLightNonSourceAnnotation
    val isAnnotationClass = (modifierList.parent as? PsiClass)?.isAnnotationType ?: false

    if (!isAnnotationClass) {
        // check getting annotations list doesn't trigger exact resolve
        modifierList.annotations

        // check searching for non-existent annotation doesn't trigger exact resolve
        modifierList.findAnnotation("some.package.MadeUpAnnotation")
    }
}

private fun String.removeLinesStartingWith(prefix: String): String {
    return lines().filterNot { it.trimStart().startsWith(prefix) }.joinToString(separator = "\n")
}
