// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnitMalformedDeclarationInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.jps.model.java.JavaResourceRootType

abstract class JUnitMalformedDeclarationInspectionTestBase(protected val junit5Version: String = JUNIT5_LATEST) : JvmInspectionTestBase() {
  override val inspection: JUnitMalformedDeclarationInspection = JUnitMalformedDeclarationInspection()

  protected open class JUnitProjectDescriptor(
    languageLevel: LanguageLevel,
    private val junit5Version: String
  ) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit4Library()
      model.addJUnit5Library(junit5Version)
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, junit5Version)

  protected fun addAutomaticExtension(text: String) {
    val servicesDir = createServiceResourceDir()
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      servicesDir.createFile(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION).also { file -> file.writeText(text) }
    }
  }

  private fun createServiceResourceDir(): VirtualFile {
    return WriteCommandAction.runWriteCommandAction(myFixture.project, Computable {
      val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("resources").also { root ->
        PsiTestUtil.addSourceRoot(myFixture.module, root, JavaResourceRootType.RESOURCE)
      }
      val metaInf = resourceRoot.createDirectory("META-INF")
      metaInf.createDirectory("services")
    })
  }

  protected companion object {
    const val JUNIT5_7_0: String = "5.7.0"
    const val JUNIT5_LATEST: String = "5.13.4"
  }
}