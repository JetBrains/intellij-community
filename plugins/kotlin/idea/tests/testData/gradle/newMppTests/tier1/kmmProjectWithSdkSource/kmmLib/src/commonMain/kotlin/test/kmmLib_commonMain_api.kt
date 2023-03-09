package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] module'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] module'")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
