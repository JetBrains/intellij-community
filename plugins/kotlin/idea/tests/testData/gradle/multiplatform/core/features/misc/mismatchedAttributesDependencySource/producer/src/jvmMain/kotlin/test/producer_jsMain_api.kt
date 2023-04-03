package test

actual class <!LINE_MARKER("descr='Has expects in project.producer.commonMain module'")!>CommonMainExpect<!> {
    actual fun <!LINE_MARKER("descr='Has expects in project.producer.commonMain module'")!>commonApi<!>() { }

    fun jvmApi() { }
}

fun produceJvmMainExpect(): CommonMainExpect = null!!
fun consumeJvmMainExpect(e: CommonMainExpect) { }
