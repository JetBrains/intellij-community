package com.intellij.grazie.hunspell

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.HttpRequests
import org.junit.Assume
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.net.HttpURLConnection
import java.util.regex.Pattern

@RunWith(JUnit4::class)
class HunspellBundleInfoTest : BasePlatformTestCase() {

  val logger: Logger = Logger.getInstance(HunspellBundleInfoTest::class.java)

  @Test
  fun `check that grazie dictionary exists`() {
    Assume.assumeTrue("Must not be run under TeamCity", !IS_UNDER_TEAMCITY)
    HunspellDescriptor.entries.forEach {
      assertTrue("Failed to verify that hunspell dictionary ${it.url} exists", isUrlValid(it.url))
    }
  }

  @ParameterizedTest
  @EnumSource(LanguageISO::class, names = ["EN", "DE", "UK", "RU"])
  fun `test hunspell-jvm version matches grazie plugin dictionary version`(iso: LanguageISO) {
    assertEquals(GraziePlugin.Hunspell.version, getDictionaryVersion(iso))
  }

  private fun isUrlValid(url: String, connectTimeoutMs: Int = 10000, readTimeoutMs: Int = 10000): Boolean {
    val responseCode = HttpRequests.head(url)
      .connectTimeout(connectTimeoutMs)
      .readTimeout(readTimeoutMs)
      .throwStatusCodeException(false)
      .tryConnect()
    return responseCode == HttpURLConnection.HTTP_OK
  }

  private fun getDictionaryVersion(iso: LanguageISO): String {
    val language = iso.name.lowercase()
    val path = HunspellBundleInfoTest::class.java.getClassLoader().getResource("dictionary/$language.aff")!!.toString()
    val matcher = Pattern.compile(".*/hunspell-$language-jvm-((\\d|.)+)\\.jar!/.*").matcher(path)
    if (!matcher.matches()) throw AssertionError("Unexpected Hunspell jar path $path")
    return matcher.group(1)
  }
}