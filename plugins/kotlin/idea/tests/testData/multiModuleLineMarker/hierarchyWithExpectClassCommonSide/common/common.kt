package test

open class <!LINE_MARKER("descr='Is subclassed by ExpectedChild [common] ExpectedChild [jvm] ExpectedChildChild ExpectedChildChildJvm SimpleChild  Click or press ... to navigate'")!>SimpleParent<!> {
    open fun <!LINE_MARKER("descr='Is overridden in test.ExpectedChild test.ExpectedChildChild test.ExpectedChildChildJvm test.SimpleChild'")!>foo<!>(n: Int) {}
    open val <!LINE_MARKER("descr='Is overridden in test.ExpectedChild test.ExpectedChildChild test.ExpectedChildChildJvm test.SimpleChild'")!>bar<!>: Int get() = 1
}

expect open class <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by ExpectedChildChild ExpectedChildChildJvm  Click or press ... to navigate'")!>ExpectedChild<!> : SimpleParent {
    override fun <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is overridden in test.ExpectedChildChild test.ExpectedChildChildJvm'"), LINE_MARKER("descr='Overrides function in 'SimpleParent''")!>foo<!>(n: Int)
    override val <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is overridden in test.ExpectedChildChild test.ExpectedChildChildJvm'"), LINE_MARKER("descr='Overrides property in 'SimpleParent''")!>bar<!>: Int
}

class ExpectedChildChild : ExpectedChild() {
    override fun <!LINE_MARKER("descr='Overrides function in 'ExpectedChild''")!>foo<!>(n: Int) {}
    override val <!LINE_MARKER("descr='Overrides property in 'ExpectedChild''")!>bar<!>: Int get() = 1
}

class SimpleChild : SimpleParent() {
    override fun <!LINE_MARKER("descr='Overrides function in 'SimpleParent''")!>foo<!>(n: Int) {}
    override val <!LINE_MARKER("descr='Overrides property in 'SimpleParent''")!>bar<!>: Int get() = 1
}
