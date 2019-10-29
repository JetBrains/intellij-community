package com.intellij.workspace.api

import org.junit.Assert
import org.junit.Test

class VirtualFileUrlTest {
  @Test
  fun testRoundTrip() {
    roundTrip("")
    roundTrip("/")
    roundTrip("foobar")
    roundTrip("file:///a")
    roundTrip("file:///")
    roundTrip("file://")
    roundTrip("file:////")
    roundTrip("file:///a/")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar!/")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar!//")
  }

  @Test
  fun normalizeSlashes() {
    Assert.assertEquals("jar://C:/Users/X/a.txt", VirtualFileUrlManager.fromUrl("jar://C:/Users\\X\\a.txt").url)
  }

  private fun roundTrip(url: String) {
    Assert.assertEquals(url, VirtualFileUrlManager.fromUrl(url).url)
  }
}