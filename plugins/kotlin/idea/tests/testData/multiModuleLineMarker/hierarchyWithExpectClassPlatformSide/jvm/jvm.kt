package test

actual open class <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is subclassed by ExpectedChildChildJvm  Click or press ... to navigate'")!>ExpectedChild<!> : SimpleParent() {
    actual override fun <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is overridden in test.ExpectedChildChildJvm'"), LINE_MARKER("descr='Overrides function in 'SimpleParent''")!>foo<!>(n: Int) {}
    actual override val <!LINE_MARKER("descr='Has expects in common module'"), LINE_MARKER("descr='Is overridden in test.ExpectedChildChildJvm'"), LINE_MARKER("descr='Overrides property in 'SimpleParent''")!>bar<!>: Int get() = 1
}

class ExpectedChildChildJvm : ExpectedChild() {
    override fun <!LINE_MARKER("descr='Overrides function in 'ExpectedChild''")!>foo<!>(n: Int) {}
    override val <!LINE_MARKER("descr='Overrides property in 'ExpectedChild''")!>bar<!>: Int get() = 1
}
