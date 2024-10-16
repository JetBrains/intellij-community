// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl

class GroovyDslScriptCheckTest: LightJavaCodeInsightFixtureTestCase() {
  fun testScriptCheckDoesNotThrowExceptionWhenNoReadAction() = runBlocking {
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

    assertNotNull(gdslFileImpl.isScript)

    val result = readAction { gdslFileImpl.isScript }
    assertTrue(result)
  }

  override fun runInDispatchThread(): Boolean = false
}
