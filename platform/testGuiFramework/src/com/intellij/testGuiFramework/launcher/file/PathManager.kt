package com.intellij.testGuiFramework.launcher.file

import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager
import java.io.File

/**
 * @author Sergey Karashevich
 */
object PathManager {

    fun getWorkDirPath() = TeamCityManager.WORK_DIR_PATH

    fun getSystemSpecificIdePath(workDir: String): String {
        when (SystemInfo.getSystemType()) {
            SystemInfo.SystemType.WINDOWS -> return workDir
            SystemInfo.SystemType.UNIX -> TODO()
            SystemInfo.SystemType.MAC -> return FileUtils.getAppFilePath(workDir)
        }
    }

    fun getSystemSpecificIdeLibPath(workDir: String): String {
        var path = getSystemSpecificIdePath(workDir)
        if (SystemInfo.getSystemType() == SystemInfo.SystemType.MAC)
            path += "${File.separator}Contents"
        path += "${File.separator}lib"
        val file = File(path)
        return file.path
    }

    fun toSysSpecPath(unixStylePath: String) = if (SystemInfo.getSystemType() == SystemInfo.SystemType.WINDOWS) unixStylePath.replace("/", File.separator) else unixStylePath

}