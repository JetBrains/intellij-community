package kmmApplication

expect class <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.main] modules'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.main] modules'")!>commonMainApi<!>()
}

fun consumeCommonMainExpect(e: CommonMainExpect?) { }
fun produceCommonMainExpect(): CommonMainExpect? = null

fun stdlibExpectLikeClass(): kotlin.RuntimeException? = null

internal fun commonMainInternal() { }
