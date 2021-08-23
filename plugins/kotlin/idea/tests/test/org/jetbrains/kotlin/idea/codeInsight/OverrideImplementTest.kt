// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.test.withCustomLanguageAndApiVersion
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
class OldOverrideImplementTest : OverrideImplementTest<OverrideMemberChooserObject>(), OldOverrideImplementTestMixIn

abstract class OverrideImplementTest<T : ClassMember> : AbstractOverrideImplementTest<T>() {
    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("codeInsight/overrideImplement")

   open fun testAndroidxNotNull() {
        doOverrideDirectoryTest("foo")
    }

   open fun testEmptyClassBodyFunctionMethod() {
        doImplementFileTest()
    }

   open fun testFunctionMethod() {
        doImplementFileTest()
    }

   open fun testFunctionProperty() {
        doImplementFileTest()
    }

   open fun testFunctionWithTypeParameters() {
        doImplementFileTest()
    }

   open fun testGenericTypesSeveralMethods() {
        doImplementFileTest()
    }

   open fun testJavaInterfaceMethod() {
        doImplementDirectoryTest()
    }

   open fun testJavaInterfaceMethodInCorrectOrder() {
        doMultiImplementDirectoryTest()
    }

   open fun testJavaParameters() {
        doImplementDirectoryTest()
    }

   open fun testFunctionFromTraitInJava() {
        doImplementJavaDirectoryTest("foo.KotlinTrait", "bar")
    }

   open fun testGenericMethod() {
        doImplementFileTest()
    }

   open fun testImplementJavaRawSubclass() {
        doImplementDirectoryTest()
    }

   open fun testProperty() {
        doImplementFileTest()
    }

   open fun testTraitGenericImplement() {
        doImplementFileTest()
    }

   open fun testDefaultValues() {
        doImplementFileTest()
    }

   open fun testRespectCaretPosition() {
        doMultiImplementFileTest()
    }

   open fun testGenerateMulti() {
        doMultiImplementFileTest()
    }

   open fun testTraitNullableFunction() {
        doImplementFileTest()
    }

   open fun testOverrideUnitFunction() {
        doOverrideFileTest()
    }

   open fun testOverrideNonUnitFunction() {
        doOverrideFileTest()
    }

   open fun testOverrideFunctionProperty() {
        doOverrideFileTest()
    }

   open fun testOverridePrimitiveProperty() {
        doMultiImplementFileTest()
    }

   open fun testOverrideGenericFunction() {
        doOverrideFileTest()
    }

   open fun testMultiOverride() {
        doMultiOverrideFileTest()
    }

   open fun testDelegatedMembers() {
        doMultiOverrideFileTest()
    }

   open fun testOverrideExplicitFunction() {
        doOverrideFileTest()
    }

   open fun testOverrideExtensionFunction() {
        doOverrideFileTest()
    }

   open fun testOverrideExtensionProperty() {
        doOverrideFileTest()
    }

   open fun testOverrideMutableExtensionProperty() {
        doOverrideFileTest()
    }

   open fun testComplexMultiOverride() {
        doMultiOverrideFileTest()
    }

   open fun testOverrideRespectCaretPosition() {
        doMultiOverrideFileTest()
    }

   open fun testOverrideJavaMethod() {
        doOverrideDirectoryTest("getAnswer")
    }

   open fun testJavaMethodWithPackageVisibility() {
        doOverrideDirectoryTest("getFooBar")
    }

   open fun testJavaMethodWithPackageProtectedVisibility() {
        doOverrideDirectoryTest("getFooBar")
    }

   open fun testPrivateJavaMethod() {
        doMultiOverrideDirectoryTest()
    }

   open fun testImplementSamAdapters() {
        doImplementDirectoryTest()
    }

   open fun testOverrideFromFunctionPosition() {
        doMultiOverrideFileTest()
    }

   open fun testOverrideFromClassName() {
        doMultiOverrideFileTest()
    }

   open fun testOverrideFromLBrace() {
        doMultiOverrideFileTest()
    }

   open fun testOverrideSamAdapters() {
        doOverrideDirectoryTest("foo")
    }

   open fun testSameTypeName() {
        doOverrideDirectoryTest()
    }

   open fun testPropagationKJK() {
        doOverrideDirectoryTest()
    }

   open fun testMultipleSupers() {
        doMultiOverrideFileTest()
    }

   open fun testNoAnyMembersInInterface() {
        doMultiOverrideFileTest()
    }

   open fun testLocalClass() {
        doImplementFileTest()
    }

   open fun testStarProjections() {
        doImplementFileTest()
    }

   open fun testEscapeIdentifiers() {
        doOverrideFileTest()
    }

   open fun testVarArgs() {
        doOverrideFileTest()
    }

   open fun testSuspendFun() {
        doOverrideFileTest()
    }

   open fun testDoNotOverrideFinal() {
        doMultiOverrideFileTest()
    }

   open fun testSuperPreference() {
        doMultiOverrideFileTest()
    }

   open fun testAmbiguousSuper() {
        doMultiOverrideFileTest()
    }

   open fun testImplementFunctionType() {
        doMultiImplementFileTest()
    }

   open fun testQualifySuperType() {
        doOverrideFileTest("f")
    }

   open fun testGenericSuperClass() {
        doOverrideFileTest("iterator")
    }

   open fun testDuplicatedAnyMembersBug() {
        doMultiOverrideFileTest()
    }

   open fun testEqualsInInterface() {
        doOverrideFileTest("equals")
    }

   open fun testCopyKDoc() {
        doOverrideFileTest("foo")
    }

   open fun testConvertJavaDoc() {
        doOverrideDirectoryTest("foo")
    }

   open fun testPlatformTypes() {
        doOverrideDirectoryTest("foo")
    }

   open fun testPlatformCollectionTypes() {
        doOverrideDirectoryTest("foo")
    }

   open fun testNullableJavaType() {
        doOverrideDirectoryTest("foo")
    }

   open fun testJavaxNonnullJavaType() {
        doOverrideDirectoryTest("foo")
    }

   open fun testNullableKotlinType() {
        doOverrideDirectoryTest("foo")
    }

   open fun testAbstractAndNonAbstractInheritedFromInterface() {
        doImplementFileTest("getFoo")
    }

   open fun testTypeAliasNotExpanded() {
        doOverrideFileTest("test")
    }

   open fun testDataClassEquals() {
        doOverrideFileTest("equals")
    }

   open fun testCopyExperimental() {
        withCustomLanguageAndApiVersion(project, module, "1.3", "1.3") {
            doOverrideFileTest("targetFun")
        }
    }

   open fun testUnresolvedType() {
        doOverrideFileTest()
    }

   open fun testImplementFromClassName() {
        doMultiImplementFileTest()
    }

   open fun testImplementFromClassName2() {
        doMultiImplementFileTest()
    }

   open fun testImplementFromClassName3() {
        doMultiImplementFileTest()
    }

   open fun testImplementFromClassName4() {
        doMultiImplementFileTest()
    }

   open fun testImplementFromClassName5() {
        doMultiImplementFileTest()
    }

   open fun testImplementFromClassName6() {
        doMultiImplementFileTest()
    }

   open fun testEnumClass() {
        doOverrideFileTest("toString")
    }

   open fun testEnumClass2() {
        doOverrideFileTest("toString")
    }

   open fun testEnumClass3() {
        doOverrideFileTest("toString")
    }

   open fun testEnumClass4() {
        doOverrideFileTest("toString")
    }

   open fun testOverrideExternalFunction() {
        doOverrideFileTest()
    }

   open fun testImplementWithComment() {
        doMultiImplementFileTest()
    }

   open fun testImplementWithComment2() {
        doMultiImplementFileTest()
    }

   open fun testImplementWithComment3() {
        doMultiImplementFileTest()
    }

   open fun testImplementWithComment4() {
        doMultiImplementFileTest()
    }

    fun testGenericClass() {
        doMultiImplementFileTest()
    }
}
