// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.TestScaleHelper
import com.intellij.util.ui.UIUtil
import io.netty.util.internal.SystemPropertyUtil
import net.miginfocom.swing.MigLayout
import org.junit.Assume
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Path
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

private val isUpdateSnapshotsGlobal by lazy { SystemPropertyUtil.getBoolean("test.update.snapshots", false) }

class NoScaleRule : ExternalResource() {
  private var scaleHelper = TestScaleHelper()

  override fun before() {
    scaleHelper.setState()
  }

  override fun after() {
    scaleHelper.restoreState()
  }
}

class RequireHeadlessMode : ExternalResource() {
  override fun before() {
    // there is some difference if run as not headless (on retina monitor, at least), not yet clear why, so, just require to run in headless mode
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      Assume.assumeTrue(GraphicsEnvironment.isHeadless())
    }
    else {
      System.setProperty("java.awt.headless", "true")
      if (!GraphicsEnvironment.isHeadless()) {
        throw RuntimeException("must be headless")
      }
    }
  }
}

fun changeLafIfNeed(lafName: String) {
  if (UIManager.getLookAndFeel().name == lafName) {
    return
  }

  invokeAndWaitIfNeed {
    UIManager.setLookAndFeel(MetalLookAndFeel())
    val laf = if (lafName == "IntelliJ") IntelliJLaf() else DarculaLaf()
    UIManager.setLookAndFeel(laf)

    if (lafName == "Darcula") {
      // static init it is hell - UIUtil static init is called too early, so, call it to init properly
      // (otherwise null stylesheet added and it leads to NPE on set comment text)
      UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", UIUtil.JBHtmlEditorKit.createStyleSheet())
    }
  }
}

fun getSnapshotRelativePath(lafName: String, isForImage: Boolean): String {
  val platformName = when {
    SystemInfo.isWindows -> "win"
    SystemInfo.isMac -> "mac"
    else -> "linux"
  }

  val result = StringBuilder()
  result.append(lafName)
  if (lafName != "Darcula" || isForImage) {
    // Darcula bounds are the same on all platforms, but images differ due to fonts (mostly)
    result.append(File.separatorChar)
    result.append(platformName)
  }

  return result.toString()
}

fun validateBounds(component: Container, snapshotDir: Path, snapshotName: String, isUpdateSnapshots: Boolean = isUpdateSnapshotsGlobal) {
  val actualSerializedLayout: String
  if (component.layout is MigLayout) {
    actualSerializedLayout = serializeLayout(component)
  }
  else {
    val dumperOptions = DumperOptions()
    dumperOptions.lineBreak = DumperOptions.LineBreak.UNIX
    val yaml = Yaml(dumperOptions)
    actualSerializedLayout = yaml
      .dump(linkedMapOf("bounds" to dumpComponentBounds(component)))
  }

  compareSnapshot(snapshotDir.resolve("$snapshotName.yml"), actualSerializedLayout, isUpdateSnapshots)
}

private fun compareSnapshot(snapshotFile: Path, newData: String, isUpdateSnapshots: Boolean) {
  if (!snapshotFile.exists()) {
    System.out.println("Write a new snapshot ${snapshotFile.fileName}")
    snapshotFile.write(newData)
    return
  }

  try {
    assertThat(newData).isEqualTo(snapshotFile)
  }
  catch (e: AssertionError) {
    if (isUpdateSnapshots) {
      System.out.println("UPDATED snapshot ${snapshotFile.fileName}")
      snapshotFile.write(newData)
    }
    else {
      throw e
    }
  }
}

internal fun validateUsingImage(component: Component, svgRenderer: SvgRenderer, snapshotName: String, isUpdateSnapshots: Boolean = isUpdateSnapshotsGlobal) {
  compareSnapshot(svgRenderer.svgFileDir.resolve("$snapshotName.svg"), svgRenderer.render(component), isUpdateSnapshots)
}

val TestName.snapshotFileName: String
  get() {
    // remove parameters name
    val name = methodName
    val bracketIndex = name.lastIndexOf('[')
    return sanitizeFileName(if (bracketIndex > 0) name.substring(0, bracketIndex) else name)
  }

internal fun dumpComponentBounds(component: Container): Map<String, IntArray> {
  val result = LinkedHashMap<String, IntArray>()
  for ((index, c) in component.components.withIndex()) {
    val bounds = c.bounds
    result.put(getComponentKey(c, index), intArrayOf(bounds.x, bounds.y, bounds.width, bounds.height))
  }
  return result
}

internal fun getComponentKey(c: Component, index: Int): String {
  if (c is JLabel && c.text.isNotEmpty()) {
    return StringUtil.removeHtmlTags(c.text)
  }
  if (c is AbstractButton && c.text.isNotEmpty()) {
    return StringUtil.removeHtmlTags(c.text)
  }
  else {
    return "${c.javaClass.simpleName} #${index}"
  }
}

fun validatePanel(userPanel: JPanel, testDataRoot: Path, snapshotName: String, lafName: String) {
  val svgRenderer = SvgRenderer(testDataRoot.resolve(getSnapshotRelativePath(lafName, isForImage = true)))

  // to run tests on retina monitor (@2x images must be not used and so on)
  // Graphics2D.getDeviceConfiguration is not enough because our IconLoader.paintIcon uses component.getGraphicsConfiguration() instead of g.getDeviceConfiguration()
//  val panel = object : JComponent() {
//    override fun getGraphicsConfiguration() = svgRenderer.deviceConfiguration
//
//    override fun paint(g: Graphics) {
//      // paint userPanel directly to ensure that SVG doesn't contain this wrapper
//      userPanel.paint(g)
//    }
//  }

//  panel.add(userPanel)

//  panel.addNotify()
  val preferredSize = userPanel.preferredSize
//  panel.setBounds(0, 0, Math.max(preferredSize.width, JBUI.scale(480)), Math.max(preferredSize.height, 320))
  userPanel.setBounds(0, 0, Math.max(preferredSize.width, JBUI.scale(480)), Math.max(preferredSize.height, 320))
  userPanel.doLayout()

  validateUsingImage(userPanel, svgRenderer, snapshotName)
  validateBounds(userPanel, testDataRoot.resolve(getSnapshotRelativePath(lafName, isForImage = false)), snapshotName)
}