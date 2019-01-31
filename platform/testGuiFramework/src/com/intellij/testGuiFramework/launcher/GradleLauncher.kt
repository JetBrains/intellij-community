package com.intellij.testGuiFramework.launcher

import com.intellij.testGuiFramework.impl.GuiTestStarter.Companion.COMMAND_NAME
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.remote.IdeControl
import org.apache.log4j.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread


object GradleLauncher {

  val LOG: Logger = Logger.getLogger(GradleLauncher::class.java)

  fun runIde(port: Int) {
    val composedArgs = composeCommandLineArgs(port)
    LOG.info("Composed command line to run IDE: $composedArgs")
    val process = ProcessBuilder().inheritIO().command(composedArgs).start()
    IdeControl.submitIdeProcess(process)
    val stdInput = BufferedReader(InputStreamReader(process.inputStream))
    val stdError = BufferedReader(InputStreamReader(process.errorStream))
    thread(start = true, name = "processStdIn") { processStdIn(stdInput) }
    thread(start = true, name = "processStdErr") { processStdErr(stdError) }
  }

  private val gradleWrapper: String by lazy {
    return@lazy if (SystemInfo.isWin()) "gradlew.bat" else "./gradlew"
  }

  private fun composeCommandLineArgs(port: Int): MutableList<String> {
    val result = mutableListOf<String>()
    return with(result) {
      add("$gradleWrapper")
      add("runIde")
      if (GuiTestOptions.isDebug) add("--debug-jvm")
      if (GuiTestOptions.isPassPrivacyPolicy) add("-Djb.privacy.policy.text=\"<!--999.999-->\"")
      if (GuiTestOptions.isPassDataSharing) add("-Djb.consents.confirmation.enabled=false")
      addIdeaAndJbProperties()
      add("-Dexec.args=$COMMAND_NAME,port=$port")
      this
    }
  }

  private fun MutableList<String>.addIdeaAndJbProperties() {
    System.getProperties().filter {
      val s = it.key as String
      s.startsWith("idea") || s.startsWith("jb")
    }.forEach{
      this.add("-D${it.key}=${it.value}")
    }
  }

  private fun String.quoteValueIfHasSpaces(): String {
    return if (Regex("\\s").containsMatchIn(this)) return "\"$this\"" else this
  }

  private fun processStdIn(stdInput: BufferedReader) {
    var s: String? = stdInput.readLine()
    while (s != null) {
      LOG.info("[IDE output]: $s")
      s = stdInput.readLine()
    }
  }

  private fun processStdErr(stdInput: BufferedReader) {
    var s: String? = stdInput.readLine()
    while (s != null) {
      LOG.warn("[IDE warn]: $s")
      s = stdInput.readLine()
    }
  }

}