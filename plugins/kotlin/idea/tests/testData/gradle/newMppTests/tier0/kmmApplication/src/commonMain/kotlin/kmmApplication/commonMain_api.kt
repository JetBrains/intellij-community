package kmmApplication

expect class <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.main] module'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.main] module'")!>commonMainApi<!>()
}

fun consumeCommonMainExpect(e: CommonMainExpect?) { }
fun produceCommonMainExpect(): CommonMainExpect? = null

fun stdlibExpectLikeClass(): kotlin.RuntimeException? = null

internal fun commonMainInternal() { }
