package test

expect class <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] modules'; targets=[(text=project.producer.jsMain; icon=nodes/Module.svg); (text=project.producer.jvmMain; icon=nodes/Module.svg)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.producer.jsMain, project.producer.jvmMain] modules'; targets=[(text=project.producer.jsMain; icon=nodes/Module.svg); (text=project.producer.jvmMain; icon=nodes/Module.svg)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
