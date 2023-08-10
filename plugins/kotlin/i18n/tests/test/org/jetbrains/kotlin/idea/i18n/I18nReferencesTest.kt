// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.i18n

import com.intellij.lang.properties.psi.Property
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class I18nReferencesTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addClass("""package org.jetbrains.annotations;
            import java.lang.annotation.*;
@Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.TYPE_USE})
public @interface PropertyKey { String resourceBundle(); }""")
        myFixture.addFileToProject("messages/MyBundle.properties", "key1=value1\nkey2=value2 {0}")
    }

    fun testResolveResourceBundleKey() {
        myFixture.configureByText("FooBar.kt", """import org.jetbrains.annotations.PropertyKey
class FooBar(key : @PropertyKey(resourceBundle = "messages.MyBundle") String) {
}
fun bar() {
    FooBar("k<caret>ey1")
}"""
        )
        val elementAtCaret = myFixture.elementAtCaret
        Assert.assertTrue(elementAtCaret.text, elementAtCaret is Property)
    }
    fun testInvalidKey() {
        myFixture.enableInspections(KotlinInvalidBundleOrPropertyInspection())
        myFixture.configureByText("FooBar.kt", """import org.jetbrains.annotations.PropertyKey
class FooBar(@PropertyKey(resourceBundle = "messages.MyBundle") key : String) {
}
fun bar() {
    FooBar(<error descr="'unknownKey' doesn't appear to be a valid property key">"unknownKey"</error>)
}"""
        )
        myFixture.testHighlighting(false, false, false)
    }

  fun testWrongNumberOfArguments() {
        myFixture.enableInspections(KotlinInvalidBundleOrPropertyInspection())
        myFixture.configureByText("FooBar.kt", """import org.jetbrains.annotations.PropertyKey
class FooBar(@PropertyKey(resourceBundle = "messages.MyBundle") key : String, vararg args: String) {
}
fun bar() {
    FooBar(<error descr="Property 'key2' expected 1 parameter, passed 0">"key2"</error>)
}"""
        )
        myFixture.testHighlighting(false, false, false)
    }
}
