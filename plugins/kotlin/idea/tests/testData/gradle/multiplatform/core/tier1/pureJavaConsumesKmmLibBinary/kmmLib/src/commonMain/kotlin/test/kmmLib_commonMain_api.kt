package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.jvmMain, project.kmmLib.iosMain] modules'; targets=[(text=project.kmmLib.jvmMain); (text=project.kmmLib.iosMain)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.jvmMain, project.kmmLib.iosMain] modules'; targets=[(text=project.kmmLib.jvmMain); (text=project.kmmLib.iosMain)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) {}
