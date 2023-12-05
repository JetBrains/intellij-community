package test

expect class <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] modules'; targets=[(text=project.kmmLib.iosMain); (text=project.kmmLib.main)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.kmmLib.iosMain, project.kmmLib.main] modules'; targets=[(text=project.kmmLib.iosMain); (text=project.kmmLib.main)]")!>commonApi<!>()
}

fun produceCommonMainExpect(): CommonMainExpect = null!!
fun consumeCommonMainExpect(e: CommonMainExpect) { }
