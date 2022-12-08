// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.jvmcompat.CompatibilitySupportUpdater
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.jvmcompat.JvmCompatibilityState
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress

class GradleJvmUpdateMatricesTest : LightIdeaTestCase() {
  companion object {
    private const val LOCALHOST = "127.0.0.1"
    private const val endpoint = "/gradle/compatibility.json"
  }

  private lateinit var myServer: HttpServer
  private lateinit var myUrl: String
  private var updateTime = 0L

  private var requests = 0;

  override fun setUp() {
    super.setUp()
    myServer = HttpServer.create().apply {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
    }

    Disposer.register(testRootDisposable) {

    }

    myUrl = "http://${LOCALHOST}:${myServer.address?.port}${endpoint}"
    updateTime = GradleJvmSupportMatrix.INSTANCE.state?.lastUpdateTime ?: 0
  }

  override fun tearDown() {
    try {
      myServer.stop(0)
      GradleJvmSupportMatrix.INSTANCE.loadState(JvmCompatibilityState().apply { isDefault = true })
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }


  }

  private fun byFile(name: String): String {
    return this.javaClass.classLoader.getResourceAsStream(name)?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText)
           ?: throw IllegalArgumentException()
  }

  private fun withResponse(serverResponse: () -> String) {
    val previous = com.intellij.openapi.util.registry.Registry.stringValue("gradle.compatibility.config.url");
    Registry.get("gradle.compatibility.config.url").setValue(myUrl)
    Disposer.register(testRootDisposable) {
      Registry.get("gradle.compatibility.config.url").setValue(previous)
    }
    myServer.createContext(endpoint) { ex: HttpExchange ->
      requests++
      try {
        val responseBody = serverResponse().toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
        ex.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBody.size.toLong())
        ex.responseBody.write(responseBody)
      }
      catch (e: Exception) {
        val responseBody = e.stackTraceToString().toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, responseBody.size.toLong())
        ex.responseBody.write(responseBody)
      }
      finally {
        ex.close()
      }
    }
  }

  fun `test update configuration`() {
    withResponse { byFile("newConfig.json") }
    PlatformTestUtil.waitForFuture(CompatibilitySupportUpdater.getInstance().checkForUpdates(), 2000)
    TestCase.assertEquals(1, requests)
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.INSTANCE.state?.data?.supportedGradleVersions!!, "1.0", "2.0")
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.INSTANCE.state?.data?.supportedJavaVersions!!, "5", "6")
    assertFalse(updateTime == GradleJvmSupportMatrix.INSTANCE.state?.lastUpdateTime)
  }

  fun `test update configuration for appropriate idea version`() {
    withResponse { byFile("newConfigWithDifferentIdea.json") }
    PlatformTestUtil.waitForFuture(CompatibilitySupportUpdater.getInstance().checkForUpdates(), 2000)
    TestCase.assertEquals(1, requests)
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.INSTANCE.state?.data?.supportedGradleVersions!!, "42.0", "43.0")
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.INSTANCE.state?.data?.supportedJavaVersions!!, "7", "8")
    assertFalse(updateTime == GradleJvmSupportMatrix.INSTANCE.state?.lastUpdateTime)
  }

  fun `test should not update configuration if error thrown`() {
    withResponse { throw Exception() }
    PlatformTestUtil.waitForFuture(CompatibilitySupportUpdater.getInstance().checkForUpdates(), 2000)
    TestCase.assertNull(GradleJvmSupportMatrix.INSTANCE.state)
  }

  fun `test should not update configuration if update interval set to 0`() {
    withResponse { throw Exception() }
    val previous = Registry.intValue("gradle.compatibility.update.interval")
    Registry.get("gradle.compatibility.update.interval").setValue(0)
    Disposer.register(testRootDisposable) {
      Registry.get("gradle.compatibility.update.interval").setValue(previous)
    }
    PlatformTestUtil.waitForFuture(CompatibilitySupportUpdater.getInstance().checkForUpdates(), 2000)
    TestCase.assertEquals(0, requests)
    TestCase.assertNull(GradleJvmSupportMatrix.INSTANCE.state)
  }
}
