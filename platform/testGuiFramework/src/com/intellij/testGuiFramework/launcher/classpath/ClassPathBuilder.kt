package com.intellij.testGuiFramework.launcher.classpath

import com.intellij.execution.CommandLineWrapperUtil
import org.apache.log4j.Logger
import java.io.File
import java.util.jar.Manifest

/**
 * @author Sergey Karashevich
 */

class ClassPathBuilder(val ideaLibPath: String,
                       val jdkPath: String,
                       val jUnitPath: String,
                       val festLibsPath: String,
                       val testGuiFrameworkPath: String) {

  private val isJar: (String) -> Boolean = { path -> path.endsWith(".jar") }
  private val isFestJar: (String) -> Boolean = { path -> path.endsWith(".jar") && path.toLowerCase().contains("fest") }
  private val toPath: (File) -> String = { file -> file.path }

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

    return (ideaLibJars + jdkJars + festJars + jUnitJar + testGuiFrameworkPath + guiTestPaths).toClasspathJarFile().path
  }

  private fun List<String>.countJars() = if (this.size == 1) "1 jar" else "${this.size} jars"

  private fun File.getFilesRecursive(): List<String> {
    return if (this.isFile) listOf(this.path)
    else this.listFiles().map { file -> file.getFilesRecursive() }.flatten()
  }

  companion object {
    val LOG: Logger = Logger.getLogger(ClassPathBuilder::class.java.canonicalName)

    fun List<String>.toClasspathJarFile(): File = createClasspathJarFile(this)

    private fun createClasspathJarFile(paths: List<String>): File {
      val file = CommandLineWrapperUtil.createClasspathJarFile(Manifest(), paths)
      LOG.info("Classpath jar has been composed: ${file.path}")
      return file
    }

  }

}