package com.intellij.testGuiFramework.launcher.file

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


/**
 * @author Sergey Karashevich
 */
object FileUtils {

    val LOG: Logger = Logger.getInstance("#com.intellij.testGuiFramework.launcher.file.FileUtils")

    fun copy(from: String, to: String, copyOption: StandardCopyOption = StandardCopyOption.REPLACE_EXISTING) {
        LOG.info("copying files from $from to $to")
        val fromPath = Paths.get(from)
        val toPath = Paths.get(to) //convert from String to Path
        Files.copy(fromPath, toPath, copyOption)
        LOG.info("done")
    }

    fun getAppFilePath(location: String): String {
        val fromPath = Paths.get(location)
        val fileApp = fromPath.toFile().listFiles().filter {file -> file.name.endsWith(".app")}.firstOrNull() ?: throw Exception("Unable to find .app file in $location")
        return fileApp.path
    }

    fun exists(location: String): Boolean = Paths.get(location).toFile().exists()

}