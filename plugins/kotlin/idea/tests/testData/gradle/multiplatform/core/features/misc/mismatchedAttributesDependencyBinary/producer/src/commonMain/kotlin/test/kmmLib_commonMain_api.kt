package test

expect class <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] modules'; targets=[(text=project.producer.jsMain); (text=project.producer.jvmMain)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] modules'; targets=[(text=project.producer.jsMain); (text=project.producer.jvmMain)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
