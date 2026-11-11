// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.remotedev

import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.kotlin.inspections.remotedev.RpcInterfaceDoesNotExtendRemoteApiInspection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@Language("kotlin")
private const val FLEET_RPC_API = """
package fleet.rpc

interface RemoteApi<Metadata>

@Target(AnnotationTarget.CLASS)
annotation class Rpc
"""

@TestApplication
class RpcInterfaceDoesNotExtendRemoteApiInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(RpcInterfaceDoesNotExtendRemoteApiInspection())

    myFixture.addFileToProject("fleet/rpc/Rpc.kt", FLEET_RPC_API)
  }

  @Test
  fun `test should report an @Rpc-annotated interface not extending RemoteApi`() {
    testHighlighting(
      """
      import fleet.rpc.Rpc
      
      @Rpc
      interface <error descr="@Rpc-annotated interface does not extend fleet.rpc.RemoteApi">RpcInterface</error> {
        suspend fun any()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test should report an @Rpc-annotated interface extending an interface but not extending RemoteApi`() {
    myFixture.addKotlinFile(
      "NonRemoteApi.kt",
      """
      interface NonRemoteApi
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.Rpc
      
      @Rpc
      interface <error descr="@Rpc-annotated interface does not extend fleet.rpc.RemoteApi">RpcInterface</error> : NonRemoteApi {
        suspend fun any()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test does not report an @Rpc-annotated interface extending RemoteApi`() {
    testHighlighting(
      """
      import fleet.rpc.RemoteApi
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApi<Unit> {
        suspend fun any()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test does not report an @Rpc-annotated interface extending an interface but not extending RemoteApi`() {
    myFixture.addKotlinFile(
      "RemoteApiBase.kt",
      """
      import fleet.rpc.RemoteApi      
      
      interface RemoteApiBase : RemoteApi<Unit>
      """.trimIndent()
    )
    testHighlighting(
      """
      import fleet.rpc.Rpc
      
      @Rpc
      interface RpcInterface : RemoteApiBase {
        suspend fun any()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test does not report an non-@Rpc-annotated interface`() {
    testHighlighting(
      """
      interface NonRpcInterface {
        fun any()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test does not report an non-@Rpc-annotated class`() {
    testHighlighting(
      """
      class NonRpcClass {
        fun any() {}
      }
      """.trimIndent()
    )
  }

  private fun testHighlighting(@Language("kotlin") code: String): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = myFixture.addKotlinFile("RpcInterface.kt", code).virtualFile
    withContext(Dispatchers.EDT) {
      myFixture.testHighlighting(true, true, true, file)
    }
  }

  private fun CodeInsightTestFixture.addKotlinFile(relativePath: String, @Language("kotlin") fileText: String): PsiFile {
    return this.addFileToProject(relativePath, fileText)
  }

}
