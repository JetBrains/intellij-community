// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.codeInsight.generation.PsiMethodMember
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class OldJava8OverrideImplementTest : Java8OverrideImplementTest<OverrideMemberChooserObject>(), OldOverrideImplementTestMixIn

abstract class Java8OverrideImplementTest<T : ClassMember> : AbstractOverrideImplementTest<T>() {
    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("codeInsight/overrideImplement/jdk8")

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK

    fun testOverrideCollectionStream() = doOverrideFileTest("stream")
    
    fun testImplementKotlinInterface() {
        val file = myFixture.addFileToProject(
            "A.kt", """interface A<T> {
    fun <L : T> foo(l : L)
}"""
        )

        val superClass = (file as KtFile).classes[0]
        val javaFile = myFixture.configureByFile(getTestName(true) + ".java")
        val psiClass = (javaFile as PsiJavaFile).classes[0]
        val method = superClass.methods[0]
        val substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, PsiSubstitutor.EMPTY)
        val candidates = listOf(PsiMethodMember(method, OverrideImplementExploreUtil.correctSubstitutor(method, substitutor)))
        myFixture.project.executeWriteCommand("") {
            OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(myFixture.editor, psiClass, candidates, false, true)
        }
        
        myFixture.checkResultByFile(getTestName(true) + ".after.java")
    }
}
