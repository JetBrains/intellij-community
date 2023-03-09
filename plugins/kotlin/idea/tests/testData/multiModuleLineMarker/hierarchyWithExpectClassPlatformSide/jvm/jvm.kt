package test

actual open class <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is subclassed by ExpectedChildChildJvm (test) Press ... to navigate'")!>ExpectedChild<!> : SimpleParent() {
    actual override fun <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is overridden in ExpectedChildChildJvm (test) Press ... to navigate'"), LINE_MARKER("descr='Overrides function in SimpleParent (test) Press ... to navigate'")!>foo<!>(n: Int) {}
    actual override val <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is overridden in ExpectedChildChildJvm (test) Press ... to navigate'"), LINE_MARKER("descr='Overrides property in SimpleParent (test) Press ... to navigate'")!>bar<!>: Int get() = 1
}

class ExpectedChildChildJvm : ExpectedChild() {
    override fun <!LINE_MARKER("descr='Overrides function in ExpectedChild (test) Press ... to navigate'")!>foo<!>(n: Int) {}
    override val <!LINE_MARKER("descr='Overrides property in ExpectedChild (test) Press ... to navigate'")!>bar<!>: Int get() = 1
}
