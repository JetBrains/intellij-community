expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>Expect<!>

interface <!LINE_MARKER("descr='Is implemented by Derived Press ... to navigate'")!>Base<!> {
    fun <!LINE_MARKER("descr='Is implemented in Derived Press ... to navigate'")!>expectInReturnType<!>(): Expect

    fun expectInArgument(e: Expect)

    fun Expect.expectInReceiver()

    val <!LINE_MARKER("descr='Is implemented in Derived Press ... to navigate'")!>expectVal<!>: Expect

    var <!LINE_MARKER("descr='Is implemented in Derived Press ... to navigate'")!>expectVar<!>: Expect
}
