import platform.posix.printf

internal fun getFormattedLogMessage(message: String): String {
    return "Linux LOG: $message"
}

actual fun <!LINE_MARKER("descr='Has expects in multiTargetIndependentProject.libOne.commonMain module'")!>writeLogMessage<!>(message: String) {
    printf("%s\n", getFormattedLogMessage(message))
}
