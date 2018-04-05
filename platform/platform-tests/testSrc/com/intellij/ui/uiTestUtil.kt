// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.netty.util.internal.SystemPropertyUtil
import net.miginfocom.swing.MigLayout
import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGraphics2D
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.awt.*
import java.io.File
import java.io.StringWriter
import java.nio.file.Path
import javax.swing.*
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.properties.Delegates

private val isUpdateSnapshotsGlobal by lazy { SystemPropertyUtil.getBoolean("test.update.snapshots", false) }

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

class FrameRule : ExternalResource() {
  var frame: JFrame by Delegates.notNull()
    private set

  private var wasFrameCreated = false

  override fun after() {
    if (wasFrameCreated) {
      invokeAndWaitIfNeed {
        frame.isVisible = false
        frame.dispose()
      }
    }
  }

  // must be called in EDT
  fun show(component: Component, minSize: Dimension? = JBDimension(480, 320)) {
    frame = createTestFrame(minSize)
    wasFrameCreated = true

    frame.contentPane.add(component, BorderLayout.CENTER)

    frame.pack()
    frame.isVisible = true

    // clear focus from first input field
    frame.requestFocusInWindow()
  }
}

private fun createTestFrame(minSize: Dimension?): JFrame {
  val frame = JFrame()
  frame.isUndecorated = true
  if (minSize != null) {
    frame.minimumSize = minSize
  }

  val screenDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
  if (SystemInfoRt.isMac && screenDevices != null && screenDevices.size > 1) {
    // use non-Retina
    for (screenDevice in screenDevices) {
      if (!UIUtil.isRetina(screenDevice)) {
        frame.setLocation(screenDevice.defaultConfiguration.bounds.x, frame.y)
        break
      }
    }
  }

  return frame
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
      .dump(linkedMapOf("bounds" to component.components.map { it.bounds }))
      .replace(" !!java.awt.Rectangle", "")
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

private fun svgGraphicsToString(svgGenerator: SVGGraphics2D): String {
  val transformer = TransformerFactory.newInstance().newTransformer()
  transformer.setOutputProperty(OutputKeys.METHOD, "xml")
  transformer.setOutputProperty(OutputKeys.INDENT, "yes")
  transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
  transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
  transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")

  val writer = StringWriter()
  writer.use {
    transformer.transform(DOMSource(svgGenerator.root), StreamResult(writer))
  }
  return writer
    .toString()
    // &#27;Remember
    // no idea why transformer/batik doesn't escape it correctly
    .replace(">&#27;", ">&amp")
}

fun validateUsingImage(component: Component, snapshotDir: Path, snapshotName: String, isUpdateSnapshots: Boolean = isUpdateSnapshotsGlobal) {
  // jFreeSvg produces not so compact and readable SVG as batik
  val svgGenerator = SVGGraphics2D(GenericDOMImplementation.getDOMImplementation().createDocument("http://www.w3.org/2000/svg", "svg", null))
  component.paint(svgGenerator)
  compareSnapshot(snapshotDir.resolve("$snapshotName.svg"), svgGraphicsToString(svgGenerator), isUpdateSnapshots)
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

fun validatePanel(panel: JPanel, testDataRoot: Path, snapshotName: String, lafName: String) {
  val preferredSize = panel.preferredSize
  panel.setBounds(0, 0, Math.max(preferredSize.width, JBUI.scale(480)), Math.max(preferredSize.height, 320))
  panel.doLayout()

  validateUsingImage(panel, testDataRoot.resolve(getSnapshotRelativePath(lafName, isForImage = true)), snapshotName)
  validateBounds(panel, testDataRoot.resolve(getSnapshotRelativePath(lafName, isForImage = false)), snapshotName)
}