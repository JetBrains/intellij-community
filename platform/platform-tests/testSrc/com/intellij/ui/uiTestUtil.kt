// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.ide.ui.html.GlobalStyleSheetHolder
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.UITestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.compareFileContent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.layout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.ui.scale.TestScaleHelper
import com.intellij.ui.scale.paint.ImageComparator
import com.intellij.util.SVGLoader
import com.intellij.util.SystemProperties
import com.intellij.util.io.exists
import com.intellij.util.io.inputStream
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.write
import kotlinx.coroutines.withContext
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
      UITestUtil.setHeadlessProperty(true)
      if (!GraphicsEnvironment.isHeadless()) {
        throw RuntimeException("must be headless")
      }
    }
  }
}

open class RestoreScaleRule : ExternalResource() {
  override fun before() {
    IconLoader.activate()
    TestScaleHelper.setState()
  }

  override fun after() {
    IconLoader.deactivate()
    TestScaleHelper.restoreState()
  }
}

internal suspend fun changeLafIfNeeded(lafName: String) {
  System.setProperty("idea.ui.set.password.echo.char", "true")

  if (UIManager.getLookAndFeel().name == lafName) {
    return
  }

  withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
    if (lafName == "Darcula") {
      // static init it is hell - UIUtil static init is called too early, so, call it to init properly
      // (otherwise null stylesheet added, and it leads to NPE on set comment text)
      UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", GlobalStyleSheetHolder.getInstance().getGlobalStyleSheet())
    }

    UIManager.setLookAndFeel(MetalLookAndFeel())
    val laf = if (lafName == "IntelliJ") IntelliJLaf() else DarculaLaf()
    UIManager.setLookAndFeel(laf)
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
fun validateBounds(component: Container, snapshotDir: Path, snapshotName: String) {
  val actualSerializedLayout: String
  if (component.layout is MigLayout) {
    actualSerializedLayout = serializeLayout(component, isIncludeCellBounds = false, isIncludeComponentBounds = false)
  }
  else {
    val dumperOptions = DumperOptions()
    dumperOptions.lineBreak = DumperOptions.LineBreak.UNIX
    val yaml = Yaml(dumperOptions)
    actualSerializedLayout = yaml
      .dump(linkedMapOf("bounds" to dumpComponentBounds(component)))
  }

  compareFileContent(actualSerializedLayout, snapshotDir.resolve("$snapshotName.yml"))
}

@Throws(FileComparisonFailure::class)
internal fun compareSvgSnapshot(snapshotFile: Path, newData: String, updateIfMismatch: Boolean) {
  if (!snapshotFile.exists()) {
    println("Write a new snapshot ${snapshotFile.fileName}")
    snapshotFile.write(newData)
    return
  }

  val uri = snapshotFile.toUri().toURL()

  val old = try {
    snapshotFile.inputStream().use { SVGLoader.load(uri, it, 1f) } as BufferedImage
  }
  catch (e: Exception) {
    if (updateIfMismatch) {
      println("UPDATED snapshot ${snapshotFile.fileName}")
      snapshotFile.write(newData)
      return
    }

    throw e
  }

  val new = newData.byteInputStream().use { SVGLoader.load(uri, it, 1f) } as BufferedImage
  val imageMismatchError = StringBuilder("images mismatch: ")
  if (ImageComparator(ImageComparator.AASmootherComparator(0.5, 0.2, Color(0, 0, 0, 0))).compare(old, new, imageMismatchError)) {
    return
  }

  try {
    compareFileContent(newData, snapshotFile, updateIfMismatch = updateIfMismatch)
  }
  catch (e: FileComparisonFailure) {
    throw MultipleFailureException(listOf(AssertionError(imageMismatchError.toString()), e))
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
  return when {
    c is JLabel && !c.text.isNullOrEmpty() -> StringUtil.removeHtmlTags(c.text, true).removeSuffix(":") + " [label]"
    c is ActionLink && !c.text.isNullOrEmpty() -> StringUtil.removeHtmlTags(c.text, true) + " [link]"
    c is AbstractButton && c.text.isNotEmpty() -> StringUtil.removeHtmlTags(c.text, true)
    c is TitledSeparator -> c.text + " [titledSeparator]"
    else -> "${c.javaClass.simpleName} #${index}"
  }
}
