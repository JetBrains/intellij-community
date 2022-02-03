// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.codeInsight

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.codeInsight.OverrideImplementTest
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@Suppress("RedundantOverride") // overrides are for easier test debugging
@RunWith(JUnit38ClassRunner::class)
internal class FirOverrideImplementTest : OverrideImplementTest<KtClassMember>(), FirOverrideImplementTestMixIn {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun testAndroidxNotNull() {
        super.testAndroidxNotNull()
    }

    override fun testEmptyClassBodyFunctionMethod() {
        super.testEmptyClassBodyFunctionMethod()
    }

    override fun testFunctionMethod() {
        super.testFunctionMethod()
    }

    override fun testFunctionProperty() {
        super.testFunctionProperty()
    }

    override fun testFunctionWithTypeParameters() {
        super.testFunctionWithTypeParameters()
    }

    override fun testGenericTypesSeveralMethods() {
        super.testGenericTypesSeveralMethods()
    }

    override fun testJavaInterfaceMethod() {
        super.testJavaInterfaceMethod()
    }

    override fun testJavaInterfaceMethodInCorrectOrder() {
        super.testJavaInterfaceMethodInCorrectOrder()
    }

    override fun testJavaParameters() {
        super.testJavaParameters()
    }

    override fun testFunctionFromTraitInJava() {
        super.testFunctionFromTraitInJava()
    }

    override fun testGenericMethod() {
        super.testGenericMethod()
    }

    override fun testImplementJavaRawSubclass() {
        super.testImplementJavaRawSubclass()
    }

    override fun testProperty() {
        super.testProperty()
    }

    override fun testTraitGenericImplement() {
        super.testTraitGenericImplement()
    }

    override fun testDefaultValues() {
        super.testDefaultValues()
    }

    override fun testRespectCaretPosition() {
        super.testRespectCaretPosition()
    }

    override fun testGenerateMulti() {
        super.testGenerateMulti()
    }

    override fun testTraitNullableFunction() {
        super.testTraitNullableFunction()
    }

    override fun testOverrideUnitFunction() {
        super.testOverrideUnitFunction()
    }

    override fun testOverrideNonUnitFunction() {
        super.testOverrideNonUnitFunction()
    }

    override fun testOverrideFunctionProperty() {
        super.testOverrideFunctionProperty()
    }

    override fun testOverridePrimitiveProperty() {
        super.testOverridePrimitiveProperty()
    }

    override fun testOverrideGenericFunction() {
        super.testOverrideGenericFunction()
    }

    override fun testMultiOverride() {
        super.testMultiOverride()
    }

    override fun testDelegatedMembers() {
        super.testDelegatedMembers()
    }

    override fun testOverrideExplicitFunction() {
        super.testOverrideExplicitFunction()
    }

    override fun testOverrideExtensionFunction() {
        super.testOverrideExtensionFunction()
    }

    override fun testOverrideExtensionProperty() {
        super.testOverrideExtensionProperty()
    }

    override fun testOverrideMutableExtensionProperty() {
        super.testOverrideMutableExtensionProperty()
    }

    override fun testComplexMultiOverride() {
        super.testComplexMultiOverride()
    }

    override fun testOverrideRespectCaretPosition() {
        super.testOverrideRespectCaretPosition()
    }

    override fun testOverrideJavaMethod() {
        super.testOverrideJavaMethod()
    }

    override fun testJavaMethodWithPackageVisibility() {
        super.testJavaMethodWithPackageVisibility()
    }

    override fun testJavaMethodWithPackageProtectedVisibility() {
        super.testJavaMethodWithPackageProtectedVisibility()
    }

    override fun testPrivateJavaMethod() {
        super.testPrivateJavaMethod()
    }

    override fun testImplementSamAdapters() {
        super.testImplementSamAdapters()
    }

    override fun testOverrideFromFunctionPosition() {
        super.testOverrideFromFunctionPosition()
    }

    override fun testOverrideFromClassName() {
        super.testOverrideFromClassName()
    }

    override fun testOverrideFromLBrace() {
        super.testOverrideFromLBrace()
    }

    override fun testOverrideSamAdapters() {
        super.testOverrideSamAdapters()
    }

    override fun testSameTypeName() {
        super.testSameTypeName()
    }

    override fun testPropagationKJK() {
        super.testPropagationKJK()
    }

    override fun testMultipleSupers() {
        super.testMultipleSupers()
    }

    override fun testNoAnyMembersInInterface() {
        super.testNoAnyMembersInInterface()
    }

    override fun testLocalClass() {
        super.testLocalClass()
    }

    override fun testStarProjections() {
        super.testStarProjections()
    }

    override fun testEscapeIdentifiers() {
        super.testEscapeIdentifiers()
    }

    override fun testVarArgs() {
        super.testVarArgs()
    }

    override fun testSuspendFun() {
        super.testSuspendFun()
    }

    override fun testDoNotOverrideFinal() {
        super.testDoNotOverrideFinal()
    }

    override fun testSuperPreference() {
        super.testSuperPreference()
    }

    override fun testAmbiguousSuper() {
        super.testAmbiguousSuper()
    }

    override fun testImplementFunctionType() {
        super.testImplementFunctionType()
    }

    override fun testQualifySuperType() {
        super.testQualifySuperType()
    }

    override fun testGenericSuperClass() {
        super.testGenericSuperClass()
    }

    override fun testDuplicatedAnyMembersBug() {
        super.testDuplicatedAnyMembersBug()
    }

    override fun testEqualsInInterface() {
        super.testEqualsInInterface()
    }

    override fun testCopyKDoc() {
        super.testCopyKDoc()
    }

    override fun testConvertJavaDoc() {
        super.testConvertJavaDoc()
    }

    override fun testPlatformTypes() {
        super.testPlatformTypes()
    }

    override fun testPlatformCollectionTypes() {
        super.testPlatformCollectionTypes()
    }

    override fun testNullableJavaType() {
        super.testNullableJavaType()
    }

    override fun testJavaxNonnullJavaType() {
        super.testJavaxNonnullJavaType()
    }

    override fun testNullableKotlinType() {
        super.testNullableKotlinType()
    }

    override fun testAbstractAndNonAbstractInheritedFromInterface() {
        super.testAbstractAndNonAbstractInheritedFromInterface()
    }

    override fun testTypeAliasNotExpanded() {
        super.testTypeAliasNotExpanded()
    }

    override fun testDataClassEquals() {
        super.testDataClassEquals()
    }

    override fun testCopyExperimental() {
        super.testCopyExperimental()
    }

    override fun testUnresolvedType() {
        super.testUnresolvedType()
    }

    override fun testImplementFromClassName() {
        super.testImplementFromClassName()
    }

    override fun testImplementFromClassName2() {
        super.testImplementFromClassName2()
    }

    override fun testImplementFromClassName3() {
        super.testImplementFromClassName3()
    }

    override fun testImplementFromClassName4() {
        super.testImplementFromClassName4()
    }

    override fun testImplementFromClassName5() {
        super.testImplementFromClassName5()
    }

    override fun testImplementFromClassName6() {
        super.testImplementFromClassName6()
    }

    override fun testEnumClass() {
        super.testEnumClass()
    }

    override fun testEnumClass2() {
        super.testEnumClass2()
    }

    override fun testEnumClass3() {
        super.testEnumClass3()
    }

    override fun testEnumClass4() {
        super.testEnumClass4()
    }

    override fun testOverrideExternalFunction() {
        super.testOverrideExternalFunction()
    }

    override fun testImplementWithComment() {
        super.testImplementWithComment()
    }

    override fun testImplementWithComment2() {
        super.testImplementWithComment2()
    }

    override fun testImplementWithComment3() {
        super.testImplementWithComment3()
    }

    override fun testImplementWithComment4() {
        super.testImplementWithComment4()
    }
}

