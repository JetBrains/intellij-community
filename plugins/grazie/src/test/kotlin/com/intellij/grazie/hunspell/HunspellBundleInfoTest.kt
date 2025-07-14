package com.intellij.grazie.hunspell

import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.HttpRequests
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.net.HttpURLConnection
import java.util.regex.Pattern

@RunWith(JUnit4::class)
class HunspellBundleInfoTest: BasePlatformTestCase() {

  val logger: Logger = Logger.getInstance(HunspellBundleInfoTest::class.java)

  @Test
  fun `check that grazie dictionary exists`() {
    Assume.assumeTrue("Must not be run under TeamCity", !IS_UNDER_TEAMCITY)
    HunspellDescriptor.entries.forEach {
      if (!isUrlValid(it.url)) {
        fail("Failed to verify that hunspell dictionary ${it.url} exists")
      }
    }
  }

  @Test
  fun `test hunspell-en-jvm version matches grazie plugin dictionary version`() {
    assertEquals(GraziePlugin.Hunspell.version, getDictionaryVersion())
  }

  private fun isUrlValid(url: String, connectTimeoutMs: Int = 10000, readTimeoutMs: Int = 10000): Boolean {
    return try {
      val responseCode = HttpRequests.head(url)
        .connectTimeout(connectTimeoutMs)
        .readTimeout(readTimeoutMs)
        .throwStatusCodeException(false) // Don't throw exceptions for non-200 status codes
        .tryConnect()
      responseCode == HttpURLConnection.HTTP_OK
    } catch (e: Exception) {
      logger.error("Request to $url failed", e)
      false
    }
  }

  private fun getDictionaryVersion(): String {
    val path = HunspellBundleInfoTest::class.java.getClassLoader().getResource("dictionary/en.aff")!!.toString()
    val matcher = Pattern.compile(".*/hunspell-en-jvm-((\\d|.)+)\\.jar!/.*").matcher(path)
    if (!matcher.matches()) throw AssertionError("Unexpected Hunspell jar path $path")
    return matcher.group(1)
  }
}