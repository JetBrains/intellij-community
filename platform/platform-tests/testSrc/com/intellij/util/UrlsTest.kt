// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.util.SystemInfoRt
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class UrlsTest {
  @Test
  fun uriSyntaxException() {
    val url = Urls.newFromEncoded(
      "http://cns-etuat-2.localnet.englishtown.com/school/e12/#school/45383/201/221/382?c=countrycode=cc|culturecode=en-us|partnercode=mkge")
    assertThat(url.authority).isEqualTo("cns-etuat-2.localnet.englishtown.com")
    assertThat(url.path).isEqualTo("/school/e12/")
    assertThat(url.scheme).isEqualTo("http")
    assertThat(url.parameters).isEqualTo("#school/45383/201/221/382?c=countrycode=cc|culturecode=en-us|partnercode=mkge")
  }

  @Test
  fun uriSyntaxException2() {
    val url = Urls.newFromEncoded("file:///test/file.js")
    assertThat(url.authority).isEmpty()
    assertThat(url.path).isEqualTo("/test/file.js")
    assertThat(url.scheme).isEqualTo("file")
    assertThat(url.parameters).isNull()
  }

  @Test
  fun windowsFilePath() {
    if (!SystemInfoRt.isWindows) {
      return
    }

    val url = Urls.newFromEncoded("file:///C:/test/file.js")
    assertThat(url.authority).isEmpty()
    assertThat(url.path).isEqualTo("C:/test/file.js")
    assertThat(url.scheme).isEqualTo("file")
    assertThat(url.parameters).isNull()
  }

  @Test
  fun uriSyntaxException3() {
    val url = Urls.newFromEncoded("http://yandex.ru")
    assertThat(url.authority).isEqualTo("yandex.ru")
    assertThat(url.path).isEmpty()
    assertThat(url.scheme).isEqualTo("http")
    assertThat(url.parameters).isNull()
  }

  @Test
  fun uriSyntaxException5() {
    val url = Urls.newFromEncoded("http://localhost/?test")
    assertThat(url.authority).isEqualTo("localhost")
    assertThat(url.path).isEqualTo("/")
    assertThat(url.scheme).isEqualTo("http")
    assertThat(url.parameters).isEqualTo("?test")
  }

  @Test
  fun uriSyntaxException4() {
    val url = Urls.newFromEncoded("http://yandex.ru#fragment")
    assertThat(url.authority).isEqualTo("yandex.ru")
    assertThat(url.path).isEqualTo("")
    assertThat(url.scheme).isEqualTo("http")
    assertThat(url.parameters).isEqualTo("#fragment")
  }

  @Test
  fun emptyIfSchemeMissed() {
    assertThat(Urls.parseEncoded("/w/qe")).isNull()
  }

  @Test
  fun localUrlToJavaUri() {
    val url = Urls.parse("/foo/bar.txt", true)
    assertThat(Urls.toUriWithoutParameters(url!!.trimParameters()).toASCIIString()).isEqualTo("file:///foo/bar.txt")
  }

  @Test
  fun eval() {
    val url = Urls.newUri("eval", "foo")
    assertThat(url.toString()).isEqualTo("eval:foo")
  }

  @Test
  fun uri() {
    val url = Urls.newFromEncoded("package:polymer/src/declaration.dart.map")
    assertThat(url.scheme).isEqualTo("package")
    assertThat(url.authority).isNull()
    assertThat(url.path).isEqualTo("polymer/src/declaration.dart.map")
  }

  @Test
  fun customSchemeAsUrl() {
    val url = Urls.newFromEncoded("webpack:///./modules/flux-orion-plugin/fluxPlugin.ts")
    assertThat(url.toDecodedForm()).isEqualTo("webpack:///./modules/flux-orion-plugin/fluxPlugin.ts")
  }

  @Test
  fun parameters() {
    val url = Urls.newFromEncoded("http://example.com").addParameters(mapOf("foo" to "bar", "a" to "c"))
    assertThat(url.toDecodedForm()).isEqualTo("http://example.com?foo=bar&a=c")
  }

  @Test
  fun parametersExisting() {
    val url = Urls.newFromEncoded("http://example.com?q=p").addParameters(mapOf("foo" to "bar", "a" to "c"))
    assertThat(url.toDecodedForm()).isEqualTo("http://example.com?q=p&foo=bar&a=c")
  }

  @Test
  fun customSchemeNotAsLocalUrlIfParseFromIdea() {
    var url = Urls.parseFromIdea("dart:developer")!!
    assertThat(url.scheme).isEqualTo("dart")
    assertThat(url.path).isEqualTo("developer")

    url = Urls.parseFromIdea("dart:core/foo")!!
    assertThat(url.scheme).isEqualTo("dart")
    assertThat(url.path).isEqualTo("core/foo")

    url = Urls.parseFromIdea("file:///hi!")!!
    assertThat(url.scheme).isEqualTo("file")
    assertThat(url.path).isEqualTo("/hi!")
  }
}