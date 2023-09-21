package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.jvmMain, project.kmmLib.iosMain] modules'; targets=[(text=project.kmmLib.jvmMain; icon=nodes/Module.svg); (text=project.kmmLib.iosMain; icon=nodes/Module.svg)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.jvmMain, project.kmmLib.iosMain] modules'; targets=[(text=project.kmmLib.jvmMain; icon=nodes/Module.svg); (text=project.kmmLib.iosMain; icon=nodes/Module.svg)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) {}
