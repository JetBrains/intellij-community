actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>MyCancelException<!> = platform.lib.MyCancellationException

actual open class <!LINE_MARKER("descr='Has expects in common module'")!>OtherException<!> : platform.lib.MyIllegalStateException()

fun test() {
    cancel(MyCancelException()) // TYPE_MISMATCH

    other(OtherException())
}
