package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] modules'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] modules'")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
