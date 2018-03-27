/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.framework.IdeTestApplication
import org.fest.swing.core.BasicComponentPrinter
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.image.ScreenshotTaker
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotOnFailure: TestWatcher() {

  override fun failed(throwable: Throwable?, description: Description?) {
    val screenshotName = "${description!!.testClass.simpleName}.${description.methodName}"
    takeScreenshotOnFailure(throwable!!, screenshotName)
  }

  companion object {
    private val LOG = Logger.getInstance(ScreenshotOnFailure::class.java)
    private val myScreenshotTaker = ScreenshotTaker()

    fun takeScreenshotOnFailure(t: Throwable, screenshotName: String) {

      try {
        val file = getOrCreateScreenshotFile(screenshotName)
        if (t is ComponentLookupException) LOG.error("${getHierarchy()} \n caused by:", t)
        myScreenshotTaker.saveDesktopAsPng(file.path)
        LOG.info("Screenshot: $file")
      }
      catch (e: Exception) {
        LOG.error("Screenshot failed. ${e.message}")
      }
    }

    fun takeScreenshot(screenshotName: String) {
      try {
        val file = getOrCreateScreenshotFile(screenshotName)
        myScreenshotTaker.saveDesktopAsPng(file.path)
        LOG.info("Screenshot: $file")
      }
      catch (e: Exception) {
        LOG.error("Screenshot failed. ${e.message}")
      }
    }

    private fun getOrCreateScreenshotFile(screenshotName: String): File {
      var file = File(IdeTestApplication.getFailedTestScreenshotDirPath(), "$screenshotName.png")
      if (file.exists())
        file = File(IdeTestApplication.getFailedTestScreenshotDirPath(), "$screenshotName.${getDateAndTime()}.png")
      file.delete()
      return file
    }

    fun getHierarchy(): String {
      val out = ByteArrayOutputStream()
      val printStream = PrintStream(out, true)
      val componentPrinter = BasicComponentPrinter.printerWithCurrentAwtHierarchy()
      componentPrinter.printComponents(printStream)
      printStream.flush()
      return String(out.toByteArray())
    }

    private fun getDateAndTime(): String {
      val dateFormat = SimpleDateFormat("yyyy_MM_dd.HH_mm_ss_SSS")
      val date = Date()
      return dateFormat.format(date) //2016/11/16 12:08:43
    }
  }


}