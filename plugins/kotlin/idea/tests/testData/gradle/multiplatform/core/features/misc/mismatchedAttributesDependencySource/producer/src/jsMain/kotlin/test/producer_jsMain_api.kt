package test

actual class <!LINE_MARKER("descr='Has expects in project.producer.commonMain module'")!>CommonMainExpect<!> {
    actual fun <!LINE_MARKER("descr='Has expects in project.producer.commonMain module'")!>commonApi<!>() { }

    fun jsApi() { }
}

fun produceJsMainExpect(): CommonMainExpect = null!!
fun consumeJsMainExpect(e: CommonMainExpect) { }
