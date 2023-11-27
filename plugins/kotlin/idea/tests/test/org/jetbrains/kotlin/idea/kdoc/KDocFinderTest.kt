// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KDocFinderTest : LightPlatformCodeInsightFixtureTestCase() {
    override fun getTestDataPath() = IDEA_TEST_DATA_DIR.resolve("kdoc/finder").slashedPath

    fun testConstructor() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        val declaration = (myFixture.file as KtFile).declarations[0]
        val descriptor = declaration.unsafeResolveToDescriptor() as ClassDescriptor
        val constructorDescriptor = descriptor.unsubstitutedPrimaryConstructor!!
        val docContent = constructorDescriptor.findKDoc()
        Assert.assertEquals("Doc for constructor of class C.", docContent!!.contentTag.getContent())
    }

    fun testAnnotated() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        val declaration = (myFixture.file as KtFile).declarations[0]
        val descriptor = declaration.unsafeResolveToDescriptor() as ClassDescriptor
        val overriddenFunctionDescriptor =
            descriptor.defaultType.memberScope.getContributedFunctions(Name.identifier("xyzzy"), NoLookupLocation.FROM_TEST).single()
        val docContent = overriddenFunctionDescriptor.findKDoc()
        Assert.assertEquals("Doc for method xyzzy", docContent!!.contentTag.getContent())
    }

    fun testOverridden() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        val declaration = (myFixture.file as KtFile).declarations.single { it.name == "Bar" }
        val descriptor = declaration.unsafeResolveToDescriptor() as ClassDescriptor
        val overriddenFunctionDescriptor =
            descriptor.defaultType.memberScope.getContributedFunctions(Name.identifier("xyzzy"), NoLookupLocation.FROM_TEST).single()
        val docContent = overriddenFunctionDescriptor.findKDoc()
        Assert.assertEquals("Doc for method xyzzy", docContent!!.contentTag.getContent())
    }

    fun testOverriddenWithSubstitutedType() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        val declaration = (myFixture.file as KtFile).declarations.single { it.name == "Bar" }
        val descriptor = declaration.unsafeResolveToDescriptor() as ClassDescriptor
        val overriddenFunctionDescriptor =
            descriptor.defaultType.memberScope.getContributedFunctions(Name.identifier("xyzzy"), NoLookupLocation.FROM_TEST).single()
        val docContent = overriddenFunctionDescriptor.findKDoc()
        Assert.assertEquals("Doc for method xyzzy", docContent!!.contentTag.getContent())
    }

    fun testProperty() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        val declaration = (myFixture.file as KtFile).declarations.single { it.name == "Foo" }
        val descriptor = declaration.unsafeResolveToDescriptor() as ClassDescriptor
        val propertyDescriptor =
            descriptor.defaultType.memberScope.getContributedVariables(Name.identifier("xyzzy"), NoLookupLocation.FROM_TEST).single()
        val docContent = propertyDescriptor.findKDoc()
        Assert.assertEquals("Doc for property xyzzy", docContent!!.contentTag.getContent())
    }

    fun testInFunctionalParam() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        Assert.assertNull(TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED))
    }

    fun testSections() {
        myFixture.configureByFile(getTestName(false) + ".kt")
        val declaration = (myFixture.file as KtFile).declarations[0]
        val descriptor = declaration.unsafeResolveToDescriptor() as ClassDescriptor
        val docContent = descriptor.findKDoc()

        val sections = docContent!!.sections
        Assert.assertEquals(2, sections.size)

        val mainSection = sections[0]
        Assert.assertEquals(docContent.contentTag, mainSection) // should be equal to default/main content
        Assert.assertEquals("General class description, first paragraph in the KDoc\n\n", mainSection.getContent())

        val additionalSection = sections[1]

        val propertyTag = additionalSection.findTagByName("property")!!
        Assert.assertEquals("name", propertyTag.getSubjectName())
        Assert.assertEquals("name property content", propertyTag.getContent())

        val paramTag = additionalSection.findTagByName("param")!!
        Assert.assertEquals("name", paramTag.getSubjectName())
        Assert.assertEquals("name param content", paramTag.getContent())

        val returnTag = additionalSection.findTagByName("return")!!
        Assert.assertEquals("return content", returnTag.getContent())

        // note: if the ordering was different, i.e @param was put before @property,
        // then it would be concatenated to the mainSection and section content would differ slightly:
        // 0: general description + @param tag content
        // 1: @property and @return
        // in this test, @property was intentionally put in between as a divider
    }
}
