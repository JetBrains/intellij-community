actual class <!LINE_MARKER("descr='Has expects in common module'")!>A<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonMember<!>() { }

    fun platformMember() { }
}

fun test() {
    A().commonMember()
    A().platformMember()
}
