// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ThrowableRunnable
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInDecompiler
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinClassFileDecompiler
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinMetadataStubBuilder
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.metadata.deserialization.Flags
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.stubs.KotlinClassStub
import org.jetbrains.kotlin.psi.stubs.elements.KtClassElementType
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol
import org.jetbrains.kotlin.serialization.deserialization.getClassId
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class BuiltInDecompilerConsistencyTest : KotlinLightCodeInsightFixtureTestCase() {
    private val classFileDecompiler = KotlinClassFileDecompiler()
    private val builtInsDecompiler = KotlinBuiltInDecompiler()

    override fun setUp() {
        super.setUp()
        BuiltInDefinitionFile.FILTER_OUT_CLASSES_EXISTING_AS_JVM_CLASS_FILES = false
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { BuiltInDefinitionFile.FILTER_OUT_CLASSES_EXISTING_AS_JVM_CLASS_FILES = true },
            ThrowableRunnable { super.tearDown() }
        )
    }

    fun testSameAsClsDecompilerForCompiledBuiltInClasses() {
        doTest(
            "kotlin",
            // ExperimentalStdlibApi is incorrectly written in built-ins, see KT-53073
            excludedClasses = setOf("ExperimentalStdlibApi")
        )
        doTest("kotlin.annotation")
        doTest("kotlin.collections")
        doTest("kotlin.ranges")
        doTest("kotlin.reflect", 3,
               setOf("KTypeProjection") // TODO: seems @JvmField is @OptionalExpectation that makes KTypeProjection actual one
        )
    }

    // Check stubs for decompiled built-in classes against stubs for decompiled JVM class files, assuming the latter are well tested
    // Check only those classes, stubs for which are present in the stub for a decompiled .kotlin_builtins file
    private fun doTest(packageFqName: String, minClassesEncountered: Int = 5, excludedClasses: Set<String> = emptySet()) {
        val dir = findDir(packageFqName, project)
        val groupedByExtension = dir.children.groupBy { it.extension }
        val builtInsFile = groupedByExtension.getValue(BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION).single()

        // do not compare commonized classes
        // proper fix is to get `expect` modifier from stub rather from bytecode metadata: https://youtrack.jetbrains.com/issue/KT-45534
        val expectClassNames = builtInsDecompiler.readFile(builtInsFile)?.let { metadata ->
            return@let if (metadata is KotlinMetadataStubBuilder.FileWithMetadata.Compatible) {
                 metadata.proto.class_List.filter { Flags.IS_EXPECT_CLASS.get(it.flags) }
                    .map { metadata.nameResolver.getClassId(it.fqName).shortClassName.asString() }.toSet()
            } else null
        } ?: emptySet()

        val classFiles = groupedByExtension.getValue(JavaClassFileType.INSTANCE.defaultExtension)
            .map { it.nameWithoutExtension }.filterNot { it in expectClassNames || it in excludedClasses }

        val builtInFileStub = builtInsDecompiler.stubBuilder.buildFileStub(FileContentImpl.createByFile(builtInsFile))!!

        val classesEncountered = arrayListOf<FqName>()

        for (className in classFiles) {
            val classFile = dir.findChild(className + "." + JavaClassFileType.INSTANCE.defaultExtension)!!
            val fileContent = FileContentImpl.createByFile(classFile)
            val file = fileContent.file
            if (ClsKotlinBinaryClassCache.getInstance().getKotlinBinaryClassHeaderData(file) == null) continue
            val fileStub = classFileDecompiler.stubBuilder.buildFileStub(fileContent) ?: continue
            val classStub = fileStub.findChildStubByType(KtClassElementType.getStubType(false)) ?: continue
            val classFqName = classStub.getFqName()!!
            val builtInClassStub = builtInFileStub.childrenStubs.firstOrNull {
                it is KotlinClassStub && it.getFqName() == classFqName
            } ?: continue
            assertEquals("Stub mismatch for $classFqName", classStub.serializeToString(), builtInClassStub.serializeToString())
            classesEncountered.add(classFqName)
        }

        assertTrue(
            "Too few classes encountered in package $packageFqName: $classesEncountered",
            classesEncountered.size >= minClassesEncountered
        )
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceNoSources()
}

internal fun findDir(packageFqName: String, project: Project): VirtualFile {
    val randomClassInPackage = KotlinFullClassNameIndex.getAllKeys(project).first {
        it.startsWith("$packageFqName.") && "." !in it.substringAfter("$packageFqName.")
    }
    val classes = KotlinFullClassNameIndex.get(randomClassInPackage, project, GlobalSearchScope.allScope(project))
    val firstClass = classes.firstOrNull() ?: error("No classes with this name found: $randomClassInPackage (package name $packageFqName)")
    return firstClass.containingFile.virtualFile.parent
}
