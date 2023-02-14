actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>Expect<!> = String

interface Derived : Base {
    override fun <!LINE_MARKER("descr='Overrides function in Base Press ... to navigate'")!>expectInReturnType<!>(): Box<Expect>

    override fun <!LINE_MARKER("descr='Overrides function in Base Press ... to navigate'")!>expectInArgument<!>(e: Box<Expect>)

    override fun Box<Expect>.<!LINE_MARKER("descr='Overrides function in Base Press ... to navigate'")!>expectInReceiver<!>()

    override val <!LINE_MARKER("descr='Overrides property in Base Press ... to navigate'")!>expectVal<!>: Box<Expect>

    override var <!LINE_MARKER("descr='Overrides property in Base Press ... to navigate'")!>expectVar<!>: Box<Expect>
}
