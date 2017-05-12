package com.intellij.testGuiFramework.launcher.classpath

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.*

/**
 * @author Sergey Karashevich
 */

class ClassPathBuilder(val ideaLibPath: String,
                       val jdkPath: String,
                       val jUnitPath: String,
                       val festLibsPath: String,
                       val testGuiFrameworkPath: String) {

  val LOG: Logger = Logger.getInstance("#com.intellij.testGuiFramework.launcher.classpath.ClassPathBuilder")

  val isJar: (String) -> Boolean = { path -> path.endsWith(".jar") }
  val isFestJar: (String) -> Boolean = { path -> path.endsWith(".jar") && path.toLowerCase().contains("fest") }
  val toPath: (File) -> String = { file -> file.path }

  fun build(guiTestPath: String) = build(listOf(guiTestPath))

  fun build(guiTestPaths: List<String>): String {
    val cp = ArrayList<String>()
    val ideaLibJars = File(ideaLibPath).listFiles().map(toPath).filter(isJar)
    val jdkJars = File(jdkPath + File.separator + "lib").getFilesRecursive().filter(isJar) +
                  File(jdkPath + File.separator + "jre" + File.separator + "lib").getFilesRecursive().filter(isJar)
    val jUnitJars = File(jUnitPath).listFiles().map(toPath).filter(isJar)
    val festJars = File(festLibsPath).listFiles().map(toPath).filter(isFestJar)

    LOG.info("Building classpath for IDE path: $ideaLibPath (${ideaLibJars.countJars()})")
    LOG.info("Building classpath for JDK path: $jdkPath (${jdkJars.countJars()})")
    LOG.info("Building classpath for JUnit path: $jUnitPath (${jUnitJars.countJars()})")
    LOG.info("Building classpath for FEST jars path: $festLibsPath (${festJars.countJars()})")
    LOG.info("Building classpath for testGuiFramework path: $testGuiFrameworkPath")
    LOG.info("Building classpath for GUI tests paths: $guiTestPaths")

    return cp.plus(ideaLibJars)
      .plus(jdkJars)
      .plus(festJars)
      .plus(jUnitJars)
      .plus(testGuiFrameworkPath)
      .plus(guiTestPaths)
      .buildOsSpec()
  }

  private fun List<String>.countJars() = if (this.size == 1) "1 jar" else "${this.size} jars"

  private fun File.getFilesRecursive(): List<String> {
    if (this.isFile) return listOf(this.path)
    else return this.listFiles().map { file -> file.getFilesRecursive() }.flatten()
  }

  companion object {
    private fun List<String>.buildOsSpec() = if (System.getProperty("os.name").toLowerCase().contains("win")) this.buildForWin()
    else this.buildForUnix()

    private fun List<String>.buildForUnix() = this.joinToString(separator = ":")
    private fun List<String>.buildForWin() = this.joinToString(separator = ";")

    fun buildOsSpecific(paths: List<String>) = paths.buildOsSpec()
  }

}