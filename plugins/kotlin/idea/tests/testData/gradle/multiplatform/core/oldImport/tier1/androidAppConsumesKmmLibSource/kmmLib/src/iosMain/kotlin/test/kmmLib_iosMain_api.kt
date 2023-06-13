package test

actual class <!LINE_MARKER("descr='Has expects in project.kmmLib.commonMain module'")!>CommonMainExpect<!> {
    actual fun <!LINE_MARKER("descr='Has expects in project.kmmLib.commonMain module'")!>commonApi<!>() { }

    fun iosApi() { }
}

fun produceIosMainExpect(): CommonMainExpect = null!!
fun consumeIosMainExpect(e: CommonMainExpect) { }
