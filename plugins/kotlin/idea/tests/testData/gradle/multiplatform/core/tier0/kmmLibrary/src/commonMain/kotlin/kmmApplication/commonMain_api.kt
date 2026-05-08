package kmmApplication

expect class <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.jvmMain] modules'; targets=[(text=project.iosMain); (text=project.jvmMain)]"), LINE_MARKER("descr='Is subclassed by CommonMainExpect [project.iosMain] (kmmApplication) CommonMainExpect [project.jvmMain] (kmmApplication) Press ... to navigate'")!>CommonMainExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in [project.iosMain, project.jvmMain] modules'; targets=[(text=project.iosMain); (text=project.jvmMain)]"), LINE_MARKER("descr='Is implemented in CommonMainExpect (kmmApplication) CommonMainExpect (kmmApplication) Press ... to navigate'")!>commonMainApi<!>()
}

fun consumeCommonMainExpect(e: CommonMainExpect?) { }
fun produceCommonMainExpect(): CommonMainExpect? = null

fun stdlibExpectLikeClass(): kotlin.RuntimeException? = null

internal fun commonMainInternal() { }
