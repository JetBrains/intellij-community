package com.intellij.testGuiFramework.launcher.zip

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

/**
 * @author Sergey Karashevich
 */
object ZipUtils{


    fun unzip(zipArchive: String, destination: String) {
//        ZipFile(zipArchive).extractAll(destination)
    }

    fun installExe(exeFilePath: String, destination: String) {
        //setup.exe /S /D=C:\test
        val processBuilder = ProcessBuilder().inheritIO().command(exeFilePath, "/S", "/D=$destination")
        val process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() == 1) {
            val errMessage = BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n"))
            System.err.println(errMessage)
            throw UnzipException("Error during installing .exe file from $exeFilePath to $destination")
        }
    }

    fun extractSit(sitArchive: String, destination: String) {
        val processBuilder = ProcessBuilder().inheritIO().command("unzip", "-q", sitArchive, "-d", destination)
        val process = processBuilder.start()
        process.waitFor()
        if (process.exitValue() == 1) {
            val errMessage = BufferedReader(InputStreamReader(process.errorStream)).lines().collect(Collectors.joining("\n"))
            System.err.println(errMessage)
            throw UnzipException("Error during expanding .sit archive from $sitArchive to $destination")
        }
    }

    class UnzipException(message: String): Exception(message)
}