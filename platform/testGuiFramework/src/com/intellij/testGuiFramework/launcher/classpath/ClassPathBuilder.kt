package com.intellij.testGuiFramework.launcher.classpath

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

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

  fun build(guiTestPath: String): String = build(listOf(guiTestPath))

  fun build(guiTestPaths: List<String>): String {
    val ideaLibJars = File(ideaLibPath).listFiles().map(toPath).filter(isJar)
    val jdkJars = File(jdkPath + File.separator + "lib").getFilesRecursive().filter(isJar) +
                  File(jdkPath + File.separator + "jre" + File.separator + "lib").getFilesRecursive().filter(isJar)
    val jUnitJar = listOf(File(jUnitPath).path)
    val festJars = File(festLibsPath).listFiles().map(toPath).filter(isFestJar)

    LOG.info("Building classpath for IDE path: $ideaLibPath (${ideaLibJars.countJars()})")
    LOG.info("Building classpath for JDK path: $jdkPath (${jdkJars.countJars()})")
    LOG.info("Building classpath for JUnit path: $jUnitPath")
    LOG.info("Building classpath for FEST jars path: $festLibsPath (${festJars.countJars()})")
    LOG.info("Building classpath for testGuiFramework path: $testGuiFrameworkPath")
    LOG.info("Building classpath for GUI tests paths: $guiTestPaths")

    return (ideaLibJars + jdkJars + festJars + jUnitJar + testGuiFrameworkPath + guiTestPaths).buildOsSpec()
  }

  private fun List<String>.countJars() = if (this.size == 1) "1 jar" else "${this.size} jars"

  private fun File.getFilesRecursive(): List<String> {
    if (this.isFile) return listOf(this.path)
    else return this.listFiles().map { file -> file.getFilesRecursive() }.flatten()
  }

  companion object {
    private fun List<String>.buildOsSpec() =
      if (isWin()) this.buildForWin()
      else this.buildForUnix()

    fun isWin(): Boolean = System.getProperty("os.name").toLowerCase().contains("win")

    private fun String.quoted() = "\"$this\""
    private fun List<String>.buildForUnix(): String = this.joinToString(separator = ":")
    private fun List<String>.buildForWin(): String = createClasspathJar(this).path

    fun buildOsSpecific(paths: List<String>): String = paths.buildOsSpec()

    private fun createClasspathJar(paths: List<String>): File {

      val resultCP = StringBuilder()
      resultCP.append("Class-Path:")
      paths.forEach { resultCP.append(" ${File(it).toURI().toURL()} \n") }

      val tempDir = FileUtilRt.createTempDirectory("forclasspath", null)
      val manifest = File(tempDir, "Manifest.mf")
      manifest.writeText(resultCP.toString())

      val binDir = File(File(System.getProperty("java.home")).parentFile, "bin")

      val jarExe = "${binDir.path}${File.separator}jar.exe"
      val pathingJar = "${tempDir}${File.separator}pathing.jar"

      val commandLine = "${jarExe.quoted()} cfm ${pathingJar.quoted()} ${manifest.path.quoted()}"
      val process = ProcessBuilder(commandLine).start()
      process.waitFor()
      if (process.exitValue() != 1) println("pathing.jar composed successfully")
      else {
        System.err.println("Process execution error:")
        System.err.println(
          BufferedReader(InputStreamReader(process!!.errorStream)).lines().collect(Collectors.joining("\n")))
      }

      return File(pathingJar)
    }

  }

}

fun main(args: Array<String>) {

  fun createClasspathJar(paths: List<String>): File {
    fun String.quoted() = "\"$this\""

    val resultCP = StringBuilder()
    resultCP.append("Class-Path:")
    paths.forEach { resultCP.append(" $it") }
    resultCP.append("\n")

    val tempDir = FileUtilRt.createTempDirectory("forclasspath", null)
    val manifest = File(tempDir, "Manifest.mf")
    manifest.writeText(resultCP.toString())

    val binDir = File(File(System.getProperty("java.home")).parentFile, "bin")

    val jarExe = "${binDir.path}${File.separator}jar.exe"
    val pathingJar = "${tempDir}${File.separator}pathing.jar"

    val commandLine = "${jarExe.quoted()} cfm ${pathingJar.quoted()} ${manifest.path.quoted()}"
    val process = ProcessBuilder(commandLine).start()
    process.waitFor()
    return File(pathingJar)
  }

  val cpJar = createClasspathJar(listOf("jar1","jar2"))
  println("Classpath jar was built: ${cpJar.path}")
}