package kmmApplication

expect class <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.jvmMain] modules'; targets=[(text=project.iosMain); (text=project.jvmMain)]")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.jvmMain] modules'; targets=[(text=project.iosMain); (text=project.jvmMain)]")!>commonMainApi<!>()
}

fun consumeCommonMainExpect(e: CommonMainExpect?) { }
fun produceCommonMainExpect(): CommonMainExpect? = null

fun stdlibExpectLikeClass(): kotlin.RuntimeException? = null

internal fun commonMainInternal() { }
