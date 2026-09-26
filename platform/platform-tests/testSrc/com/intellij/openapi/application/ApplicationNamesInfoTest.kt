// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.xml.dom.XmlElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.writeText

private const val CUSTOM_PRODUCT = "CustomProductFromFile"

class ApplicationNamesInfoTest {
  @Test
  fun `custom application info file is read for the Gateway prefix`(@TempDir dir: Path) {
    val data = loadRawData(prefix = PlatformUtils.GATEWAY_PREFIX, file = writeCustomAppInfo(dir))
    assertThat(productName(data)).isEqualTo(CUSTOM_PRODUCT)
  }

  @Test
  fun `custom application info file is ignored for a non-Gateway prefix`(@TempDir dir: Path) {
    val expected = loadRawData(prefix = currentPrefix(), file = null)
    val data = loadRawData(prefix = currentPrefix(), file = writeCustomAppInfo(dir))
    assertThat(productName(data)).isNotEqualTo(CUSTOM_PRODUCT)
    assertThat(productName(data)).isEqualTo(productName(expected))
  }

  @Test
  fun `raw data is cached and a later prefix change has no effect`() {
    val first = withPrefix(currentPrefix()) { ApplicationNamesInfo.initAndGetRawData() }
    val second = withPrefix("NoSuchProductForTest") { ApplicationNamesInfo.initAndGetRawData() }
    assertThat(second).isSameAs(first)
  }

  private fun currentPrefix(): String {
    val prefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "")
    assertThat(prefix).isNotEqualTo(PlatformUtils.GATEWAY_PREFIX)
    return prefix
  }

  private fun writeCustomAppInfo(dir: Path): Path {
    val file = dir.resolve("info.xml")
    file.writeText("""<state><names product="$CUSTOM_PRODUCT" fullname="$CUSTOM_PRODUCT"/></state>""")
    return file
  }

  private fun loadRawData(prefix: String, file: Path?): XmlElement {
    return withPrefix(prefix) {
      val result = AtomicReference<XmlElement>()
      PlatformTestUtil.withSystemProperty<RuntimeException>(ApplicationNamesInfo.APPLICATION_INFO_FILE_PROPERTY, file?.toString()) {
        result.set(ApplicationNamesInfo.loadData())
      }
      result.get()
    }
  }

  private fun <T> withPrefix(prefix: String, action: () -> T): T {
    val result = AtomicReference<T>()
    PlatformTestUtil.withSystemProperty<RuntimeException>(PlatformUtils.PLATFORM_PREFIX_KEY, prefix) {
      result.set(action())
    }
    return result.get()
  }

  private fun productName(data: XmlElement): String? = data.getChild("names")?.getAttributeValue("product")
}
