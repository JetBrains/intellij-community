package test

expect class <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] modules'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] modules'")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
