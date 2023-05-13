package test

expect class <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] module'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] module'")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
