// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubs

import com.intellij.psi.stubs.StubElement
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.debugText.getDebugText
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementType
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class DebugTextByStubTest : LightJavaCodeInsightFixtureTestCase() {
    private fun createFileAndStubTree(text: String): Pair<KtFile, StubElement<*>> {
        val file = myFixture.configureByText("test.kt", text) as KtFile
        val stub = KtFileStubBuilder().buildStubTree(file)
        return Pair(file, stub)
    }

    private fun createStubTree(text: String) = createFileAndStubTree(text).second

    private inline fun <reified E : KtElement> StubElement<*>.asKtElement(): E = psi as E

    fun packageDirective(text: String) {
        val (file, tree) = createFileAndStubTree(text)
        val packageDirective = tree.findChildStubByElementType(KtNodeTypes.PACKAGE_DIRECTIVE)!!
        val psi = packageDirective.asKtElement<KtPackageDirective>()
        Assert.assertEquals(file.packageDirective!!.text, psi.getDebugText())
    }

    fun function(text: String) {
        val (file, tree) = createFileAndStubTree(text)
        val function = tree.findChildStubByElementType(KtNodeTypes.FUN)!!
        val psi = function.asKtElement<KtNamedFunction>()
        Assert.assertEquals("STUB: " + file.findChildByClass(KtNamedFunction::class.java)!!.text, psi.getDebugText())
    }

    fun typeReference(text: String) {
        val (file, tree) = createFileAndStubTree("fun foo(i: $text)")
        val typeReferenceStub = tree.findChildStubByElementType(KtNodeTypes.FUN)!!
            .findChildStubByElementType(KtNodeTypes.VALUE_PARAMETER_LIST)!!
            .findChildStubByElementType(KtNodeTypes.VALUE_PARAMETER)!!
            .findChildStubByElementType(KtNodeTypes.TYPE_REFERENCE)!!

        val psiFromStub = typeReferenceStub.asKtElement<KtTypeReference>()
        val typeReferenceByPsi = file.findChildByClass(KtNamedFunction::class.java)!!.valueParameters[0].typeReference
        Assert.assertEquals(typeReferenceByPsi!!.text, psiFromStub.getDebugText())
    }

    fun clazz(text: String, expectedText: String? = null) {
        val (file, tree) = createFileAndStubTree(text)
        val clazz = tree.findChildStubByElementType(KtNodeTypes.CLASS)!!
        val psiFromStub = clazz.asKtElement<KtClass>()
        val classByPsi = file.findChildByClass(KtClass::class.java)
        val toCheckAgainst = "STUB: " + (expectedText ?: classByPsi!!.text)
        Assert.assertEquals(toCheckAgainst, psiFromStub.getDebugText())
        if (expectedText != null) {
            Assert.assertNotEquals("Expected text should not be specified", classByPsi!!.getDebugText(), psiFromStub.getDebugText())
        }
    }

    fun obj(text: String, expectedText: String? = null) {
        val (file, tree) = createFileAndStubTree(text)
        val obj = tree.findChildStubByElementType(KtNodeTypes.OBJECT_DECLARATION)!!
        val psiFromStub = obj.asKtElement<KtObjectDeclaration>()
        val objectByPsi = file.findChildByClass(KtObjectDeclaration::class.java)
        val toCheckAgainst = "STUB: " + (expectedText ?: objectByPsi!!.text)
        Assert.assertEquals(toCheckAgainst, psiFromStub.getDebugText())
    }

    fun property(text: String, expectedText: String? = null) {
        val (file, tree) = createFileAndStubTree(text)
        val property = tree.findChildStubByElementType(KtNodeTypes.PROPERTY)!!
        val psiFromStub = property.asKtElement<KtProperty>()
        val propertyByPsi = file.findChildByClass(KtProperty::class.java)
        val toCheckAgainst = "STUB: " + (expectedText ?: propertyByPsi!!.text)
        Assert.assertEquals(toCheckAgainst, psiFromStub.getDebugText())
    }

    fun importList(text: String) {
        val (file, tree) = createFileAndStubTree(text)
        val importList = tree.findChildStubByElementType(KtNodeTypes.IMPORT_LIST)!!
        val psi = importList.asKtElement<KtImportList>()
        Assert.assertEquals(file.importList!!.text, psi.getDebugText())
    }

    fun testPackageDirective() {
        packageDirective("package a.b.c")
        packageDirective("")
        packageDirective("package b")
    }

    fun testImportList() {
        importList("import a\nimport b.c.d")
        importList("import a.*")
        importList("import a.c as Alias")
    }

    fun testFunction() {
        function("fun foo()")
        function("fun <T> foo()")
        function("fun <T> foo()")
        function("fun <T, G> foo()")
        function("fun foo(a: Int, b: String)")
        function("fun Int.foo()")
        function("fun foo(): String")
        function("fun <T> T.foo(b: T): List<T>")
        function("fun <T, G> f() where T : G")
        function("fun <T, G> f() where T : G, G : T")
        function("private final fun f()")
    }

    fun testTypeReference() {
        typeReference("T")
        typeReference("T<G>")
        typeReference("T<G, H>")
        typeReference("T<in G>")
        typeReference("T<out G>")
        typeReference("T<*>")
        typeReference("T<*, in G>")
        typeReference("T?")
        typeReference("T<G?>")
        typeReference("() -> T")
        typeReference("(G?, H) -> T?")
        typeReference("L.(G?, H) -> T?")
        typeReference("L?.(G?, H) -> T?")
    }

    fun testClass() {
        clazz("class A")
        clazz("open private class A", expectedText = "private open class A")
        clazz("public class private A")
        clazz("class A()")
        clazz("class A() : B()", expectedText = "class A() : B")
        clazz("class A() : B<T>")
        clazz("class A() : B(), C()", expectedText = "class A() : B, C")
        clazz("class A() : B by g", expectedText = "class A() : B")
        clazz("class A() : B by g, C(), T", expectedText = "class A() : B, C, T")
        clazz("class A(i: Int, g: String)")
        clazz("class A(val i: Int, var g: String)")
    }

    fun testObject() {
        obj("object Foo")
        obj("public final object Foo")
        obj("object Foo : A()", expectedText = "object Foo : A")
        obj("object Foo : A by foo", expectedText = "object Foo : A")
        obj("object Foo : A, T, C by g, B()", expectedText = "object Foo : A, T, C, B")
    }

    fun testProperty() {
        property("val c: Int")
        property("var c: Int")
        property("var : Int")
        property("private final var c: Int")
        property("val g")
        property("val g = 3", expectedText = "val g")
        property("val g by z", expectedText = "val g")
        property("val g: Int by z", expectedText = "val g: Int")
    }

    fun testClassBody() {
        val tree = createStubTree("class A {\n {} fun f(): Int val c: Int}")
        val classBody = tree.findChildStubByElementType(KtNodeTypes.CLASS)!!
            .findChildStubByElementType(KtNodeTypes.CLASS_BODY)!!

        assertEquals("class body for STUB: class A", classBody.asKtElement<KtClassBody>().getDebugText())
    }

    fun testClassInitializer() {
        val tree = createStubTree("class A {\n init {} }")
        val initializer = tree.findChildStubByElementType(KtNodeTypes.CLASS)!!
            .findChildStubByElementType(KtNodeTypes.CLASS_BODY)!!
            .findChildStubByElementType(KtNodeTypes.CLASS_INITIALIZER)!!

        assertEquals(
            "initializer in STUB: class A",
            initializer.asKtElement<KtClassInitializer>().getDebugText()
        )
    }

    fun testClassObject() {
        val tree = createStubTree("class A { companion object Def {} }")
        val companionObject = tree.findChildStubByElementType(KtNodeTypes.CLASS)!!
            .findChildStubByElementType(KtNodeTypes.CLASS_BODY)!!
            .findChildStubByElementType(KtNodeTypes.OBJECT_DECLARATION)!!

        assertEquals("STUB: companion object Def", companionObject.asKtElement<KtObjectDeclaration>().getDebugText())
    }

    fun testPropertyAccessors() {
        val tree = createStubTree("var c: Int\nget() = 3\nset(i: Int) {}")
        val accessors = tree.findChildStubByElementType(KtNodeTypes.PROPERTY)!!
            .getChildrenByType(
                KtNodeTypes.PROPERTY_ACCESSOR,
                (KtNodeTypes.PROPERTY_ACCESSOR as KtStubElementType<*, *>).arrayFactory,
            )

        assertEquals("getter for STUB: var c: Int", accessors[0].getDebugText())
        assertEquals("setter for STUB: var c: Int", accessors[1].getDebugText())
    }

    fun testEnumEntry() {
        val tree = createStubTree("enum class Enum { E1, E2(), E3(1, 2)}")
        val entries = tree.findChildStubByElementType(KtNodeTypes.CLASS)!!
            .findChildStubByElementType(KtNodeTypes.CLASS_BODY)!!
            .getChildrenByType(
                KtNodeTypes.ENUM_ENTRY,
                (KtNodeTypes.ENUM_ENTRY as KtStubElementType<*, *>).arrayFactory,
            )

        assertEquals("STUB: enum entry E1", entries[0].getDebugText())
        assertEquals("STUB: enum entry E2 : Enum", entries[1].getDebugText())
        assertEquals("STUB: enum entry E3 : Enum", entries[2].getDebugText())
    }
}
