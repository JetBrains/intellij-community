package com.intellij.testGuiFramework.launcher.ide

import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager
import com.intellij.testGuiFramework.launcher.teamcity.TeamCityManager.PLATFORM_PREFIX
import java.util.*

/**
 * @author Sergey Karashevich
 */
class IdeTestFixture(val ide: Ide) {

    private var testList: MutableList<String> = ArrayList()
    private var configPath: String? = null
    private var systemPath: String? = null
    private var pluginList: MutableList<String> = ArrayList()
    private val ideRunArgs: IdeRunArgs = IdeRunArgs(ArrayList(), ArrayList(), ArrayList())

    private var configureAfterInstallation = false


    fun config(customConfigPath: String) {
        configPath = customConfigPath
    }

    fun system(customSystemPath: String) {
        systemPath = customSystemPath
    }

    fun jvmConfig(param: String) {
        ideRunArgs.jvmParams.add(param)
    }

    fun jvmConfig(params: List<String>) {
        ideRunArgs.jvmParams.addAll(params)
    }

    fun jvmConfig(vararg params: String) {
        ideRunArgs.jvmParams.addAll(params.toList())
    }

    fun debug(transport: String = "dt_socket", server: Boolean = true, suspend: Boolean = true, port: Int) {
        if (!ideRunArgs.jvmParams.contains("-Xdebug")) ideRunArgs.jvmParams.add("-Xdebug")
        ideRunArgs.jvmParams.forEach { jvmParameter -> if (jvmParameter.startsWith("-Xrunjdwp:")) ideRunArgs.jvmParams.remove(jvmParameter)} //remove all debug specific params
        ideRunArgs.jvmParams.add("-Xrunjdwp:transport=$transport,server=${if (server) "y" else "n"},suspend=${if (suspend) "y" else "n"},address=$port")
    }

    fun firstStart() { configureAfterInstallation = true }


    fun installPlugin(pluginToInstall: String) {
        installPlugins(listOf(pluginToInstall))
    }

    fun installPlugins(pluginsToInstall: List<String>) {
        pluginList.addAll(pluginsToInstall)
    }

    fun test(testClassName: String) {
        testList.add(testClassName)
    }

    fun buildArgs(): IdeRunArgs {
        if (pluginList.isNotEmpty()) {
            val pluginsStr = pluginList.joinToString(", ")
            ideRunArgs.ideParams.add("-DGUI_TEST_ADD_PLUGINS=$pluginsStr")
            ideRunArgs.testClasses.clear()
            ideRunArgs.testClasses.add("com.intellij.testGuiFramework.impl.PluginInstaller")
        } else {
            ideRunArgs.testClasses.addAll(testList)
        }
        if (configureAfterInstallation) ideRunArgs.ideParams.add("-DGUI_FIRST_START=true")
        if (!configPath.isNullOrEmpty()) ideRunArgs.ideParams.add("-DCUSTOM_CONFIG_PATH=$configPath")
        if (ide.ideType.platformPrefix.isNotEmpty()) ideRunArgs.ideParams.add("-Didea.platform.prefix=${ide.ideType.platformPrefix}") else "-Didea.platform.prefix=${PLATFORM_PREFIX}"
        ideRunArgs.ideParams.add("-DGUI_TEST_DATA_DIR=${TeamCityManager.GUI_TEST_DATA_DIR}")
        return ideRunArgs
    }
}

data class IdeRunArgs(val jvmParams: MutableList<String>, val ideParams: MutableList<String>, val testClasses: MutableList<String>) {
    override fun toString(): String {
        return "IdeRunArgs(jvmParams=(${jvmParams.joinToString(", ")}), ideParams=(${ideParams.joinToString(", ")}), testClasses=${testClasses.joinToString(", ")})"
    }
}
