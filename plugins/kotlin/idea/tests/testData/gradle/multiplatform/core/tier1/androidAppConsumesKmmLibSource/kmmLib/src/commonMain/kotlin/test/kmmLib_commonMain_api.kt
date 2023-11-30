package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] modules'; targets=[(text=project.kmmLib.iosMain; icon=nodes/Module.svg); (text=project.kmmLib.main; icon=nodes/Module.svg)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] modules'; targets=[(text=project.kmmLib.iosMain; icon=nodes/Module.svg); (text=project.kmmLib.main; icon=nodes/Module.svg)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
