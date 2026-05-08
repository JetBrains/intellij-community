import platform.posix.printf

internal fun getFormattedLogMessage(message: String): String {
    return "Linux LOG: $message"
}

actual fun <!LINE_MARKER("descr='Has expects in multiTargetIndependentProject.libOne.commonMain module'"), LINE_MARKER("descr='Implements function in Common.kt Press ... to navigate'")!>writeLogMessage<!>(message: String) {
    printf("%s\n", getFormattedLogMessage(message))
}
