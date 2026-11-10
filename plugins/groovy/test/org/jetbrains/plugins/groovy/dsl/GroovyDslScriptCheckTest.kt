// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class GroovyDslScriptCheckTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testScriptCheckDoesNotThrowExceptionWhenCalledFromBackgroundThread(): Unit = runBlocking {
    val gdslFile = myFixture.configureByText("script.gdsl",
                                             """
                                def ctx = context(ctype: "java.lang.String")
                                
                                contributor ([ctx], {
                                  method name:"zzz", type:"void", params:[:]
                                })
                                """
    )
    assertNotNull(gdslFile)
    assertTrue(gdslFile is GroovyFileImpl)

    val gdslFileImpl = gdslFile as GroovyFileImpl

    assertTrue(gdslFileImpl.isScript)

    val result = readAction { gdslFileImpl.isScript }
    assertTrue(result)
  }
}
