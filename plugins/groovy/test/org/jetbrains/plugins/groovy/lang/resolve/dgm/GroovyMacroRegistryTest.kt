// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.dgm.DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Checks that the macro registry follows the extension modules of the sources.
 * The library of [GroovyProjectDescriptors.GROOVY_LATEST] has an extension module, so the registry is not empty from the start.
 */
@TestApplication
class GroovyMacroRegistryTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST)
  }

  private val fixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testMacroAddedAfterRegistryIsComputed() {
    val context = addMacroRuntime()
    assertEquals(emptyList<String>(), knownMacros(context))

    addMacroExtensionModule()

    assertEquals(listOf("myMacro"), knownMacros(context))
  }

  @Test
  fun testMacroRemovedAfterRegistryIsComputed() {
    val context = addMacroRuntime()
    val extensionModule = addMacroExtensionModule()
    assertEquals(listOf("myMacro"), knownMacros(context))

    WriteAction.runAndWait<IOException> { extensionModule.virtualFile.delete(this) }

    assertEquals(emptyList<String>(), knownMacros(context))
  }

  /** Adds the macro annotation and the macro context of the Groovy runtime, and returns a file of the module. */
  private fun addMacroRuntime(): PsiFile {
    fixture.addClass("package org.codehaus.groovy.macro.runtime; public @interface Macro {}")
    fixture.addClass("package org.codehaus.groovy.macro.runtime; public class MacroContext {}")
    return fixture.addFileToProject("context.groovy", "")
  }

  /** Adds the class `ext.Macros` with the macro `myMacro`, and returns the extension module file that declares the class. */
  private fun addMacroExtensionModule(): PsiFile {
    fixture.addClass("""
      package ext;
      import org.codehaus.groovy.macro.runtime.Macro;
      import org.codehaus.groovy.macro.runtime.MacroContext;
      public class Macros {
        @Macro public static Object myMacro(MacroContext context, Object argument) { return null; }
      }
    """.trimIndent())
    return fixture.addFileToProject("META-INF/services/$ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE", """
      moduleName=ext-module
      moduleVersion=1.0
      extensionClasses=ext.Macros
    """.trimIndent())
  }

  private fun knownMacros(context: PsiFile): List<String> {
    IndexingTestUtil.waitUntilIndexesAreReady(context.project)
    return ReadAction.computeBlocking<List<String>, RuntimeException> {
      context.project.getService(GroovyMacroRegistryService::class.java).getAllKnownMacros(context).map { it.name }.sorted()
    }
  }
}
