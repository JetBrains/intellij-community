// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.execution.junit.codeInspection.JUnitMalformedDeclarationInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.pom.java.LanguageLevel
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.util.concurrent.ConcurrentHashMap

abstract class JUnitMalformedDeclarationInspectionTestBase(protected vararg val versions: MavenTestLib) : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = JUnitMalformedDeclarationInspection()

  private data class ProjectDescriptorCacheKey(val languageLevel: LanguageLevel, val versions: Set<MavenTestLib>)

  private val projectDescriptorCache = ConcurrentHashMap<ProjectDescriptorCacheKey, LightProjectDescriptor>()

  override fun getProjectDescriptor(): LightProjectDescriptor =
    projectDescriptorCache.computeIfAbsent(ProjectDescriptorCacheKey(HIGHEST, versions.toSet())) {
      JUnitProjectDescriptor(it.languageLevel, *it.versions.toTypedArray())
    }

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
}