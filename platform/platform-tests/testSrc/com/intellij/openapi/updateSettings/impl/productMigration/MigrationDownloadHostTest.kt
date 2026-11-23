// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.intellij.util.io.DigestUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.HttpURLConnection
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes

@TestFixtures
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MigrationDownloadHostTest {
  private val serverFixture = localhostHttpServer()
  private val server get() = serverFixture.get()

  @TempDir
  lateinit var workDir: Path

  private val data = ByteArray(100_000) { it.toByte() }
  private val requests = ConcurrentHashMap<String, AtomicInteger>()

  private val isAllowedUrl: (String) -> Boolean = { it.startsWith("${server.url}/ok/") }
  private val isAllowedFinalUrl: (String) -> Boolean = { it.startsWith("${server.url}/ok/") || it.startsWith("${server.url}/mirror/") }

  @Test
  fun `redirect to an allowed host works`() {
    redirect("/ok/a.dmg.sha256", "/ok/b.dmg.sha256")
    content("/ok/b.dmg.sha256", DigestUtil.sha256Hex(data).toByteArray())
    redirect("/ok/a.dmg", "/ok/b.dmg")
    content("/ok/b.dmg", data)

    val file = download("/ok/a.dmg")
    assertThat(file.readBytes()).isEqualTo(data)
  }

  @Test
  fun `redirect of the distribution to a host that is not allowed saves no file`() {
    content("/ok/a.dmg.sha256", DigestUtil.sha256Hex(data).toByteArray())
    redirect("/ok/a.dmg", "/bad/a.dmg")
    content("/bad/a.dmg", data)

    val error = assertThrows<MigrationInstallException> { download("/ok/a.dmg") }
    assertThat(error.userMessage).isEqualTo(IdeBundle.message("updates.migration.content.invalid"))
    assertThat(error.message).contains("${server.url}/ok/a.dmg", "${server.url}/bad/a.dmg")
    assertThat(workDir.listDirectoryEntries()).isEmpty()
  }

  @Test
  fun `redirect of the checksum to a host that is not allowed stops before the download`() {
    redirect("/ok/a.dmg.sha256", "/bad/a.dmg.sha256")
    content("/bad/a.dmg.sha256", DigestUtil.sha256Hex(data).toByteArray())
    content("/ok/a.dmg", data)

    val error = assertThrows<MigrationInstallException> { download("/ok/a.dmg") }
    assertThat(error.userMessage).isEqualTo(IdeBundle.message("updates.migration.error.checksum"))
    assertThat(requests).doesNotContainKey("/ok/a.dmg")
    assertThat(workDir.listDirectoryEntries()).isEmpty()
  }

  @Test
  fun `first URL that is not allowed sends no request`() {
    content("/bad/a.dmg.sha256", DigestUtil.sha256Hex(data).toByteArray())
    content("/bad/a.dmg", data)

    val error = assertThrows<MigrationInstallException> { download("/bad/a.dmg") }
    assertThat(error.userMessage).isEqualTo(IdeBundle.message("updates.migration.content.invalid"))
    assertThat(requests).isEmpty()
    assertThat(workDir.listDirectoryEntries()).isEmpty()
  }

  @Test
  fun `redirect to a mirror works`() {
    redirect("/ok/a.dmg.sha256", "/mirror/a.dmg.sha256")
    content("/mirror/a.dmg.sha256", DigestUtil.sha256Hex(data).toByteArray())
    redirect("/ok/a.dmg", "/mirror/a.dmg")
    content("/mirror/a.dmg", data)

    assertThat(download("/ok/a.dmg").readBytes()).isEqualTo(data)
  }

  @Test
  fun `first URL on a mirror sends no request`() {
    content("/mirror/a.dmg.sha256", DigestUtil.sha256Hex(data).toByteArray())
    content("/mirror/a.dmg", data)

    val error = assertThrows<MigrationInstallException> { download("/mirror/a.dmg") }
    assertThat(error.userMessage).isEqualTo(IdeBundle.message("updates.migration.content.invalid"))
    assertThat(requests).isEmpty()
  }

  private fun download(path: String): Path =
    downloadAndVerify("${server.url}${path}", workDir, indicator(), isAllowedUrl, isAllowedFinalUrl)

  private fun redirect(path: String, target: String) {
    server.createContext(path) { exchange ->
      count(path)
      exchange.responseHeaders.add("Location", "${server.url}${target}")
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1)
      exchange.close()
    }
  }

  private fun content(path: String, bytes: ByteArray) {
    server.createContext(path) { exchange ->
      count(path)
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
  }

  private fun count(path: String) {
    requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
  }

  private fun indicator(): EmptyProgressIndicator = EmptyProgressIndicator(ModalityState.nonModal())
}
