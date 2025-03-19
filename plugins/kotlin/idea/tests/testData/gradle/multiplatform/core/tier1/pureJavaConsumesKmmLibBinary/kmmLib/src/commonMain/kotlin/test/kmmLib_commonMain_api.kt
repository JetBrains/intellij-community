package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.jvmMain] modules'; targets=[(text=project.kmmLib.iosMain); (text=project.kmmLib.jvmMain)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.jvmMain] modules'; targets=[(text=project.kmmLib.iosMain); (text=project.kmmLib.jvmMain)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) {}
