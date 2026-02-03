import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.getenv
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual fun <!LINE_MARKER("descr='Has expects in multiTargetIndependentProject.libTwo.commonMain module'")!>getUserPlatformInfo<!>(): String {
    val username = getenv("USERNAME")?.toKString() ?: "Unknown User"
    return "You're on Windows. Logged in as: $username"
}
