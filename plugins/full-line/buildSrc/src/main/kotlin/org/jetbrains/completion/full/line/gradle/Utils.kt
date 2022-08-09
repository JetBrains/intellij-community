package org.jetbrains.completion.full.line.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra


abstract class IdeaVersions {
    val type = "IU"
    abstract val since: String
    abstract val target: String
    abstract val until: String

    class IDEA202 : IdeaVersions() {
        override val since = "202.6397.94"
        override val target = "202.6397.94"
        override val until = "203.4203.26"
    }

    class IDEA203 : IdeaVersions() {
        override val since = "203.4203.26"
        override val target = "203.6682.168"
        override val until = "223.*"
    }
}

object Plugins {
    const val mlRanking = "completionMlRanking"
    const val java = "java"
    val kotlin = when (Utils.get().platformVersion) {
        "202" -> "org.jetbrains.kotlin:1.4.10-release-IJ2020.2-1"
        else  -> "org.jetbrains.kotlin"
    }
    val python = "Pythonid:${Utils.get().ideaVersion.target.removeSuffix("-EAP-SNAPSHOT")}"
    val js = "JavaScriptLanguage"
}

/**
 * Must be initialised at the very **top** of root `build.gradle.kts` file by calling `Utils.init(project)`
 */
class Utils private constructor(val project: Project) {
    val allPlatformVersions: List<String> = prop("allPlatformVersions").toVersionList()
    val platformVersion: String = prop("platformVersion")
    val ideaVersion: IdeaVersions = when (prop("platformVersion")) {
        "202" -> IdeaVersions.IDEA202()
        "203" -> IdeaVersions.IDEA203()
        else  -> throw IllegalStateException("Passed platformVersion is not configured yet")
    }

    fun disableSubprojectTask(taskName: String) = project.subprojects.forEach {
        it.tasks.findByName(taskName)?.enabled = false
    }

    fun prop(name: String): String = project.extra.properties[name] as? String
        ?: error("Property `$name` is not defined in gradle.properties")

    private fun String.toVersionList(): List<String> {
        val pattern = Regex("^\\d{1,3}([,]?\\s*\\d{3})*\$")
        if (!pattern.matches(this)) {
            throw IllegalStateException("Passed incorrect version list, must be 1 to 3 digit numbers separated by comma")
        }
        return this.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {

        private lateinit var utils: Utils

        fun init(project: Project) {
            utils = Utils(project)
        }

        fun get(): Utils {
            if (!::utils.isInitialized) {
                throw UninitializedPropertyAccessException("Utils obj weren't initialised with project, call `Utils.get(project)` earlier")
            }
            return utils
        }
    }
}


fun <T> T.store(key: String): T {
    return also { Utils.get().project.extra.set(key, it) }
}
