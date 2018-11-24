package com.intellij.testGuiFramework.launcher.teamcity

/**
 * @author Sergey Karashevich
 */
object TeamCityManager {

    val baseUrl: String  = "http://buildserver.labs.intellij.net"
    val guestAuth = "guestAuth/repository/download"

    val JDK_PATH: String = getSystemOrEnv("JDK_PATH")
    val IDEA_PATH: String = getSystemOrEnv("IDEA_PATH")
    val FEST_LIB_PATH: String = getSystemOrEnv("FEST_LIB_PATH")
    val TEST_CLASSES_DIR: String = getSystemOrEnv("TEST_CLASSES_DIR")
    val TEST_GUI_FRAMEWORK_PATH: String = getSystemOrEnv("TEST_GUI_FRAMEWORK_PATH")
    val GUI_TEST_DATA_DIR: String = getSystemOrEnv("GUI_TEST_DATA_DIR")
    val WORK_DIR_PATH: String = getSystemOrEnv("WORK_DIR_PATH")
    val JUNIT_PATH: String = getSystemOrEnv("JUNIT_PATH")
    val PLATFORM_PREFIX: String? = getSystemOrEnvNoSafe("PLATFORM_PREFIX")
    val CUSTOM_CONFIG_PATH: String? = getSystemOrEnvNoSafe("CUSTOM_CONFIG_PATH")

    fun getSystemOrEnv(key: String): String {
        try {
            return if (System.getenv(key) != null) System.getenv(key) else System.getProperty(key)
        } catch(e: IllegalStateException) {
            throw IllegalStateException("${e.message}: caused by key: \"$key\"")
        }
    }

    fun getSystemOrEnvNoSafe(key: String): String? {
        try {
            return if (System.getenv(key) != null) System.getenv(key) else System.getProperty(key)
        } catch(e: IllegalStateException) {
            System.err.println("${e.message}: caused by key: \"$key\"")
            return null
        }
    }
}