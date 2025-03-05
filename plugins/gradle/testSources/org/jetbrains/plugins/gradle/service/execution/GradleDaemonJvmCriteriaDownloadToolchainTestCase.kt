// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkProduct
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@TestApplication
abstract class GradleDaemonJvmCriteriaDownloadToolchainTestCase {

  private val projectFixture = projectFixture()
  val project: Project get() = projectFixture.get()

  @TestDisposable
  private lateinit var testDisposable: Disposable

  @BeforeEach
  fun setUp() {
    val expectedJdkItems = listOf(
      simpleJdkItem("Oracle", "OpenJDK", 21),
      simpleJdkItem("Amazon", "Corretto", 21),
      simpleJdkItem("BellSoft", "Liberica JDK", 21),
      simpleJdkItem("Azul", "Zulu Community™", 17),
      simpleJdkItem("Azul", "Zulu Community™", 21),
      simpleJdkItem("SAP", "SapMachine", 21),
      simpleJdkItem("Eclipse", "Temurin", 21),
      simpleJdkItem("IBM", "Semeru", 21),
      simpleJdkItem("GraalVM", "Community Edition", 21),
      simpleJdkItem("JetBrains", "Runtime", 17),
      simpleJdkItem("JetBrains", "Runtime", 21)
    )
    val jdkListDownloader = mock<JdkListDownloader>().apply {
      whenever(downloadModelForJdkInstaller(anyOrNull(), any())).thenReturn(expectedJdkItems)
    }
    application.replaceService(JdkListDownloader::class.java, jdkListDownloader, testDisposable)
  }

  private fun simpleJdkItem(vendor: String, product: String, version: Int): JdkItem {
    return mock<JdkItem>().also {
      whenever(it.product).thenReturn(JdkProduct(vendor = vendor, product = product, flavour = null))
      whenever(it.jdkMajorVersion).thenReturn(version)
      whenever(it.installFolderName).thenReturn("installFolderName")
    }
  }
}