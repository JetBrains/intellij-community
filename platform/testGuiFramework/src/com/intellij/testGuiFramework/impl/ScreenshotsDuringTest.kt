package com.intellij.testGuiFramework.impl

import com.intellij.openapi.util.io.FileUtil.ensureExists
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testGuiFramework.framework.GuiTestPaths
import com.intellij.testGuiFramework.util.ScreenshotTaker
import com.intellij.testGuiFramework.util.logError
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Rule that takes a screenshot every second.
 */
class ScreenshotsDuringTest @JvmOverloads constructor(private val myPeriod: Int = 100, private val keepScreenshots: Boolean = false) : TestWatcher() {
  private val myScreenshotTaker = ScreenshotTaker()
  private val myExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
  private var myFolder: File? = null
  private var isTestSuccessful = false
  private val screenshotName
    get() = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss.SSS").format(System.currentTimeMillis()) + ".jpg"


  override fun starting(description: Description) {
    val folderName = description.testClass.simpleName + "-" + description.methodName
    try {
      myFolder = File(GuiTestPaths.failedTestScreenshotDir, folderName)
      ensureExists(myFolder!!)
    }
    catch (e: IOException) {
      logError("Could not create folder $folderName")
    }

    myExecutorService.scheduleAtFixedRate({ myScreenshotTaker.safeTakeScreenshotAndSave(File(myFolder, screenshotName)) },
                                          100, myPeriod.toLong(), TimeUnit.MILLISECONDS)
  }

  override fun finished(description: Description) {
    myExecutorService.shutdown()
    try {
      myExecutorService.awaitTermination(myPeriod.toLong(), TimeUnit.MILLISECONDS)
    }
    catch (e: InterruptedException) {
      // Do not report the timeout
    }
    if(keepScreenshots.not() && isTestSuccessful && myFolder != null) FileUtilRt.delete(myFolder!!)
  }

  override fun succeeded(description: Description?) {
    isTestSuccessful = true
  }

}
