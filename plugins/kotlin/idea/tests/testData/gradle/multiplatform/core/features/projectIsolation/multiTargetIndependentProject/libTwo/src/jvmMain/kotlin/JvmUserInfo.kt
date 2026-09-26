actual fun <!LINE_MARKER("descr='Has expects in multiTargetIndependentProject.libTwo.commonMain module'"), LINE_MARKER("descr='Implements function in UserGreeting.kt Press ... to navigate'")!>getUserPlatformInfo<!>(): String {
    val userHome = System.getProperty("user.home") ?: "Unknown Home"
    return "You're on JVM. Home directory: $userHome"
}
