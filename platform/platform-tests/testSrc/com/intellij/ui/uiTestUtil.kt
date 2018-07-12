// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.compareFileContent
import com.intellij.ui.layout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.SVGLoader
import com.intellij.util.SystemProperties
import com.intellij.util.io.exists
import com.intellij.util.io.inputStream
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import com.intellij.util.ui.TestScaleHelper
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.paint.ImageComparator
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.runners.model.MultipleFailureException
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.lang.AssertionError
import java.nio.file.Path
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

internal val isUpdateSnapshotsGlobal by lazy { SystemProperties.getBooleanProperty("test.update.snapshots", false) }

open class RequireHeadlessMode : ExternalResource() {
  override fun before() {
    // there is some difference if run as not headless (on retina monitor, at least), not yet clear why, so, just require to run in headless mode
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      // assumeTrue("headless mode is required", GraphicsEnvironment.isHeadless())
      // on TC headless is not enabled
    }
    else {
      System.setProperty("java.awt.headless", "true")
      if (!GraphicsEnvironment.isHeadless()) {
        throw RuntimeException("must be headless")
      }
    }
  }
}

open class RestoreScaleRule : ExternalResource() {
  override fun before() {
    TestScaleHelper.setState()
  }

  override fun after() {
    TestScaleHelper.restoreState()
  }
}

fun changeLafIfNeed(lafName: String) {
  System.setProperty("idea.ui.set.password.echo.char", "true")

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

// even bounds snapshot cannot be for all OS, because width depends on font and font is different on each OS
fun getSnapshotRelativePath(lafName: String): String {
  val platformName = when {
    SystemInfo.isWindows -> "win"
    SystemInfo.isMac -> "mac"
    else -> "linux"
  }

  val result = StringBuilder()
  result.append(lafName)
  // Darcula bounds are the same on all platforms, but images differ due to fonts (mostly)
  result.append(File.separatorChar)
  result.append(platformName)

  return result.toString()
}

@Throws(FileComparisonFailure::class)
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

@Throws(FileComparisonFailure::class)
internal fun compareSvgSnapshot(snapshotFile: Path, newData: String, isUpdateSnapshots: Boolean) {
  if (!snapshotFile.exists()) {
    System.out.println("Write a new snapshot ${snapshotFile.fileName}")
    snapshotFile.write(newData)
    return
  }

  val uri = snapshotFile.toUri().toURL()

  fun updateSnapshot() {
    System.out.println("UPDATED snapshot ${snapshotFile.fileName}")
    snapshotFile.write(newData)
  }

  val old = try {
    snapshotFile.inputStream().use { SVGLoader.load(uri, it, 1.0) } as BufferedImage
  }
  catch (e: Exception) {
    if (isUpdateSnapshots) {
      updateSnapshot()
      return
    }

    throw e
  }

  val new = newData.byteInputStream().use { SVGLoader.load(uri, it, 1.0) } as BufferedImage
  val imageMismatchError = StringBuilder("images mismatch: ")
  if (ImageComparator(ImageComparator.AASmootherComparator(0.1, 0.1, Color(0, 0, 0, 0))).compare(old, new, imageMismatchError)) {
    return
  }

  try {
    compareFileContent(newData, snapshotFile)
  }
  catch (e: FileComparisonFailure) {
    if (isUpdateSnapshots) {
      updateSnapshot()
      return
    }
    else {
      throw MultipleFailureException(listOf(AssertionError(imageMismatchError.toString()), e))
    }
  }
}

@Throws(FileComparisonFailure::class)
internal fun compareSnapshot(snapshotFile: Path, newData: String, isUpdateSnapshots: Boolean) {
  if (!snapshotFile.exists()) {
    System.out.println("Write a new snapshot ${snapshotFile.fileName}")
    snapshotFile.write(newData)
    return
  }

  try {
    compareFileContent(newData, snapshotFile)
  }
  catch (e: FileComparisonFailure) {
    if (isUpdateSnapshots) {
      System.out.println("UPDATED snapshot ${snapshotFile.fileName}")
      snapshotFile.write(newData)
    }
    else {
      throw e
    }
  }
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
