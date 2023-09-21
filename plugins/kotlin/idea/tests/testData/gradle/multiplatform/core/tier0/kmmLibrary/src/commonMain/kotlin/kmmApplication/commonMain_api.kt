package kmmApplication

expect class <!LINE_MARKER("descr='Has actuals in [project.jvmMain, project.iosMain] modules'; targets=[(text=project.jvmMain; icon=nodes/Module.svg); (text=project.iosMain; icon=nodes/Module.svg)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.jvmMain, project.iosMain] modules'; targets=[(text=project.jvmMain; icon=nodes/Module.svg); (text=project.iosMain; icon=nodes/Module.svg)]")!>commonMainApi<!>()
}

fun consumeCommonMainExpect(e: CommonMainExpect?) { }
fun produceCommonMainExpect(): CommonMainExpect? = null

fun stdlibExpectLikeClass(): kotlin.RuntimeException? = null

internal fun commonMainInternal() { }
