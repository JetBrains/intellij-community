expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>Expect<!>

class Box<out T>(val x: T)

interface <!LINE_MARKER("descr='Is implemented by Derived Press ... to navigate'")!>Base<!> {
    fun <!LINE_MARKER("descr='Is implemented in Derived Press ... to navigate'")!>expectInReturnType<!>(): Box<Expect>

    fun expectInArgument(e: Box<Expect>)

    fun Box<Expect>.expectInReceiver()

    val <!LINE_MARKER("descr='Is implemented in Derived Press ... to navigate'")!>expectVal<!>: Box<Expect>

    var <!LINE_MARKER("descr='Is implemented in Derived Press ... to navigate'")!>expectVar<!>: Box<Expect>
}
