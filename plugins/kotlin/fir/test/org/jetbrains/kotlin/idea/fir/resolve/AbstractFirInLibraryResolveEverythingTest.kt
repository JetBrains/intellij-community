// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.psi.stubs.StubElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtElement
import java.io.File

abstract class AbstractFirInLibraryResolveEverythingTest : KotlinLightCodeInsightFixtureTestCase() {
  private val mockLibraryFacility = MockLibraryFacility(
    source = IDEA_TEST_DATA_DIR.resolve("resolve/compiled/_library"),
    attachSources = false
  )

  override fun isFirPlugin(): Boolean {
    return true
  }

  override fun getProjectDescriptor(): LightProjectDescriptor =
    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

  override fun setUp() {
    super.setUp()
    mockLibraryFacility.setUp(module)
  }

  fun doTest(filePath: String) {
    myFixture.configureByFile(filePath)
    val resolve = myFixture.getReferenceAtCaretPosition()!!.resolve() as KtElement
    val ktFileStub = resolve.containingKtFile.stub!!
    val builder = StringBuilder()
    checkStubs(ktFileStub, builder)
    KotlinTestUtils.assertEqualsToFile(File("$filePath.txt"), builder.toString())
  }

  private fun checkStubs(stubElement: StubElement<*>, builder: StringBuilder) {
    stubElement.childrenStubs.forEach { stub ->
      val psi = stub.psi as StubBasedPsiElementBase<*>
      val reference = psi.reference
      val resolve = reference?.resolve()
      if (resolve != null) {
        builder.append(resolve.toString()).append("\n")
      }
      assertNotNull(psi.stub)
      checkStubs(stub, builder)
    }
  }

  override fun tearDown() {
    runAll(
      ThrowableRunnable { project.invalidateCaches() },
      ThrowableRunnable { mockLibraryFacility.tearDown(module) },
      ThrowableRunnable { super.tearDown() }
    )
  }
}
