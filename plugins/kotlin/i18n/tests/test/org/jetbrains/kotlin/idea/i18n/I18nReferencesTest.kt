// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.i18n

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil.addDependency
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class I18nReferencesTest : JavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1

    override fun setUp() {
        setUpWithKotlinPlugin {
            super.setUp()
            myFixture.addClass(
                """package org.jetbrains.annotations;
            import java.lang.annotation.*;
@Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.TYPE_USE})
public @interface PropertyKey { String resourceBundle(); }"""
            )
            myFixture.addFileToProject("messages/MyBundle.properties", "key1=value1\nkey2=value2 {0}")
        }
    }

    fun testResolveResourceBundle() {
        myFixture.configureByText("FooBar.kt", """
            import org.jetbrains.annotations.PropertyKey
            class FooBar(key : @PropertyKey(resourceBundle = "m<caret>essages.MyBundle") String) {}
            fun bar() {
                FooBar("key1")
            }
            """.trimIndent()
        )
        val elementAtCaret = myFixture.elementAtCaret
        Assert.assertTrue(elementAtCaret.text, elementAtCaret is PropertiesFile)
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

    fun testResolveResourceBundleFromRuntimeScope() {
        val resourcesRoot = myFixture.tempDirFixture.findOrCreateDir("resourcesModule/resources")
        myFixture.tempDirFixture.createFile(
            "resourcesModule/resources/messages/RuntimeScopeBundle.properties",
            "runtimeKey=runtimeValue\n"
        )
        val resourcesModule = addJavaModuleWithResources(
            moduleRoot = "resourcesModule",
            moduleName = "resources.module",
            resourcesRoot = resourcesRoot
        )

        addDependency(module, resourcesModule, DependencyScope.RUNTIME, /* exported = */ false)
        myFixture.configureByText(
            "FooBar.kt", """
                import org.jetbrains.annotations.PropertyKey
                class FooBar(key : @PropertyKey(resourceBundle = "m<caret>essages.RuntimeScopeBundle") String) {}
                fun bar() {
                    FooBar("runtimeKey")
                }
            """.trimIndent()
        )
        val elementAtCaret = myFixture.elementAtCaret
        Assert.assertTrue(elementAtCaret.text, elementAtCaret is PropertiesFile)
    }

    fun testResolveResourceBundleKeyFromRuntimeScope() {
        val resourcesRoot = myFixture.tempDirFixture.findOrCreateDir("resourcesModule/resources")
        myFixture.tempDirFixture.createFile(
            "resourcesModule/resources/messages/RuntimeScopeBundle.properties",
            "runtimeKey=runtimeValue\n"
        )
        val resourcesModule = addJavaModuleWithResources(
            moduleRoot = "resourcesModule",
            moduleName = "resources.module",
            resourcesRoot = resourcesRoot
        )

        addDependency(module, resourcesModule, DependencyScope.RUNTIME, /* exported = */ false)
        myFixture.configureByText(
            "FooBar.kt", """
                import org.jetbrains.annotations.PropertyKey
                class FooBar(key : @PropertyKey(resourceBundle = "messages.RuntimeScopeBundle") String) {}
                fun bar() {
                    FooBar("r<caret>untimeKey")
                }
            """.trimIndent()
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

    private fun addJavaModuleWithResources(
        moduleRoot: String,
        moduleName: String,
        resourcesRoot: VirtualFile
    ): Module {
        val newModule = PsiTestUtil.addModule(
            project,
            JavaModuleType.getModuleType(),
            moduleName,
            myFixture.tempDirFixture.findOrCreateDir(moduleRoot)
        )
        PsiTestUtil.addResourceContentToRoots(newModule, resourcesRoot, /* testResource = */ false)
        return newModule
    }
}
