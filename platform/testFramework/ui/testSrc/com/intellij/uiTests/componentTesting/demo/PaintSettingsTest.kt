// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.demo

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.uiTests.componentTesting.SettingsComponentToTest
import org.junit.Test
import java.nio.file.Path

class PaintSettingsTest {

  // Example of collecting UI images
  @Test
  fun test() {
    val remoteRobot = RemoteRobot("http://127.0.0.1:8580")
    val list = remoteRobot.callJs<ArrayList<String>>("""
        importClass(com.intellij.ide.actions.ShowSettingsUtilImpl)
        const list = ShowSettingsUtilImpl.getConfigurables(null, true)
        const result = new ArrayList()
        for (var i = 0 ; i < list.length; i++) {
            result.add(list[i].getId())
        }
        result
    """)
    list.forEach { id ->
      remoteRobot.runJs("""
        importPackage(com.intellij.uiTests.componentTesting.canvas)
        ComponentTesting.INSTANCE.show(new ${SettingsComponentToTest::class.java.canonicalName}('$id'))
      """.apply { println(this) })
      try {
        val frame = remoteRobot.find(CommonContainerFixture::class.java, byXpath("//div[@title='${SettingsComponentToTest::class.java.canonicalName}']"))
        Thread.sleep(500)
        frame.paintToFile(Path.of("${id.replace("/", "")}.png"))
      } finally {
        try {
          remoteRobot.runJs("""
            importPackage(com.intellij.uiTests.componentTesting.canvas)
            ComponentTesting.INSTANCE.close()
          """)
        } catch (e: Throwable) {
          println("Failed to close Frame")
        }
      }
    }
  }
}

fun ComponentFixture.paintToFile(path: Path) = callJs<ByteArray>(
  """
                        importPackage(java.io)
                        importPackage(javax.imageio)
                        importPackage(java.awt.image)
                        const screenShot = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        component.paint(screenShot.getGraphics())
                        let pictureBytes;
                        const baos = new ByteArrayOutputStream();
                        try {
                            ImageIO.write(screenShot, "png", baos);
                            pictureBytes = baos.toByteArray();
                        } finally {
                          baos.close();
                        }
                        pictureBytes;   
            """, true
).apply { path.toFile().writeBytes(this) }