actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>Expect<!> = String

interface Derived : Base {
    override fun <!LINE_MARKER("descr='Overrides function in Base Press ... to navigate'")!>expectInReturnType<!>(): Expect

    override fun <!LINE_MARKER("descr='Overrides function in Base Press ... to navigate'")!>expectInArgument<!>(e: Expect)

    override fun Expect.<!LINE_MARKER("descr='Overrides function in Base Press ... to navigate'")!>expectInReceiver<!>()

    override val <!LINE_MARKER("descr='Overrides property in Base Press ... to navigate'")!>expectVal<!>: Expect

    override var <!LINE_MARKER("descr='Overrides property in Base Press ... to navigate'")!>expectVar<!>: Expect
}
