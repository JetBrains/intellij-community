package com.intellij.testGuiFramework.launcher.download

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.launcher.file.FileUtils
import com.intellij.testGuiFramework.launcher.file.PathManager
import com.intellij.testGuiFramework.launcher.file.PathManager.getSystemSpecificIdeLibPath
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeType
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.baseUrl
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.guestAuth
import com.intellij.testGuiFramework.launcher.zip.ZipUtils
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels


/**
 * @author Sergey Karashevich
 */
object IdeDownloader {

    val LOG: Logger = Logger.getInstance("#com.intellij.testGuiFramework.launcher.download.IdeDownloader")

    val buildArtifactPath = "-{build.number}"
    val unscramblePath = "unscrambled"


    fun download(url: URL, pathFile: String) {
        LOG.info("downloading from URL: $url to $pathFile ...")
        val rbc = Channels.newChannel(url.openStream())
        val fos = FileOutputStream(pathFile)
        fos.channel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)
        LOG.info("downloading done")
        fos.close()
    }

    fun unpack(pathFile: String) {
        //check extension
        LOG.info("unpacking $pathFile in the same dir ...")
        val workDir = File(pathFile).parentFile
        when(SystemInfo.getSystemType()) {

            SystemInfo.SystemType.WINDOWS -> ZipUtils.installExe(pathFile, workDir.path)
            SystemInfo.SystemType.UNIX -> TODO()
            SystemInfo.SystemType.MAC -> ZipUtils.extractSit(pathFile, workDir.path)
        }
        LOG.info("unpacking done")
    }

    fun unscramble(ide: Ide, workDir: String) {
        val pathToFile = "$workDir${File.separator}idea.jar"
        LOG.info("unscrambling $pathToFile")
        download(buildUnscrambleUrl(ide), pathToFile)
        FileUtils.copy(from = pathToFile, to = "${getSystemSpecificIdeLibPath(workDir)}${File.separator}idea.jar")
        LOG.info("unscrambling done")
    }

    fun buildUrl(ide: Ide, extension: String = "zip"): URL = URL("${baseUrl}/${guestAuth}/${ide.ideType.buildTypeExtId}/${ide.version}.${ide.build}/${ide.ideType.id}$buildArtifactPath.$extension")
    fun buildUnscrambleUrl(ide: Ide): URL = URL("${baseUrl}/${guestAuth}/${ide.ideType.buildTypeExtId}/${ide.version}.${ide.build}/$unscramblePath/${ide.ideType.ideJarName}")

    @JvmStatic
    fun main(args: Array<String>) {
        val ide = Ide(ideType = IdeType.IDEA_COMMUNITY, version = 171, build = 3085)
        val ext = SystemInfo.getExt()
        val pathToSave = PathManager.getWorkDirPath()
        val path = "$pathToSave${File.separator}${ide.ideType.id}-${ide.version}.${ide.build}.$ext"

        download(buildUrl(ide = ide, extension = ext), path)
        unpack(path)
        unscramble(ide, pathToSave)
    }
}