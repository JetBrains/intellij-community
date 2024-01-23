// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.UsefulTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilityState
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilitySupportUpdater
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress

private const val LOCALHOST = "127.0.0.1"
private const val endpoint = "/gradle/compatibility.json"

class GradleJvmUpdateMatricesTest : LightIdeaTestCase() {
  private lateinit var myServer: HttpServer
  private lateinit var myUrl: String
  private val zeroUpdateTime = 0L

  private var requests = 0

  override fun setUp() {
    super.setUp()
    myServer = HttpServer.create().apply {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
    }

    Disposer.register(testRootDisposable) {

    }

    myUrl = "http://${LOCALHOST}:${myServer.address?.port}${endpoint}"
    GradleJvmSupportMatrix.getInstance().resetState()
  }

  override fun tearDown() {
    try {
      myServer.stop(0)
      val state = GradleCompatibilityState()
      state.isDefault = false
      GradleJvmSupportMatrix.getInstance().loadState(state)
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
    val previous = Registry.stringValue("gradle.compatibility.config.url")
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
    GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    assertEquals(1, requests)
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.getInstance().state?.supportedGradleVersions!!, "1.0", "2.0")
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.getInstance().state?.supportedJavaVersions!!, "5", "6")
    assertFalse(zeroUpdateTime == GradleJvmSupportMatrix.getInstance().state?.lastUpdateTime)
  }

  fun `test should not update configuration twice`() {
    withResponse { byFile("newConfig.json") }
    GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    assertEquals(1, requests)
    val newUpdateTime = GradleJvmSupportMatrix.getInstance().state?.lastUpdateTime
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.getInstance().state?.supportedGradleVersions!!, "1.0", "2.0")
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.getInstance().state?.supportedJavaVersions!!, "5", "6")
    assertFalse(zeroUpdateTime == newUpdateTime)
    requests = 0

    GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    assertEquals(0, requests)
    assertEquals(newUpdateTime, GradleJvmSupportMatrix.getInstance().state?.lastUpdateTime)

  }

  fun `test update configuration for appropriate idea version`() {
    withResponse { byFile("newConfigWithDifferentIdea.json") }
    GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    assertEquals(1, requests)
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.getInstance().state?.supportedGradleVersions!!, "42.0", "43.0")
    UsefulTestCase.assertSameElements(GradleJvmSupportMatrix.getInstance().state?.supportedJavaVersions!!, "7", "8")
    assertFalse(zeroUpdateTime == GradleJvmSupportMatrix.getInstance().state?.lastUpdateTime)
  }

  fun `test should not update configuration if error thrown`() {
    withResponse { throw Exception() }
    GradleJvmSupportMatrix.getInstance().noStateLoaded()
    val previousState = GradleJvmSupportMatrix.getInstance().state
    GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    assertEquals(GradleJvmSupportMatrix.getInstance().state, previousState)
  }

  fun `test should not update configuration if update interval set to 0`() {
    withResponse { throw Exception() }
    val previous = Registry.intValue("gradle.compatibility.update.interval")
    Registry.get("gradle.compatibility.update.interval").setValue(0)
    Disposer.register(testRootDisposable) {
      Registry.get("gradle.compatibility.update.interval").setValue(previous)
    }
    val previousState = GradleJvmSupportMatrix.getInstance().state
    GradleCompatibilitySupportUpdater.getInstance().checkForUpdates()
    assertEquals(0, requests)
    assertEquals(GradleJvmSupportMatrix.getInstance().state, previousState)
  }
}
