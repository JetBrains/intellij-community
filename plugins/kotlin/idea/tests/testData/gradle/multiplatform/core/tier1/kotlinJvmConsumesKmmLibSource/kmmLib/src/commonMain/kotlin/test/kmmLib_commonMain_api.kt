package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.jvmMain, project.kmmLib.iosMain] modules'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.jvmMain, project.kmmLib.iosMain] modules'")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
