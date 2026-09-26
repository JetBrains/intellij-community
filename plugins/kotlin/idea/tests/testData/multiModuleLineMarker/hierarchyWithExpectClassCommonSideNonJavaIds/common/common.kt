package test

open class <!LINE_MARKER("descr='Is subclassed by ExpectedChild [common] (test) ExpectedChild [jvm] (test) ExpectedChildChild (test) ExpectedChildChildJvm (test) SimpleChild (test) Press ... to navigate'")!>SimpleParent<!> {
    open fun <!LINE_MARKER("descr='Is overridden in ExpectedChild (test) ExpectedChild (test) ExpectedChildChild (test) ExpectedChildChildJvm (test) SimpleChild (test) Press ... to navigate'")!>`foo fun`<!>(n: Int) {}
    open val <!LINE_MARKER("descr='Is overridden in ExpectedChild [common] (test) ExpectedChild [jvm] (test) ExpectedChildChild (test) ExpectedChildChildJvm (test) SimpleChild (test) Press ... to navigate'")!>`bar fun`<!>: Int get() = 1
}

expect open class <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by ExpectedChildChild (test) ExpectedChildChildJvm (test) Press ... to navigate'")!>ExpectedChild<!> : SimpleParent {
    override fun <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is overridden in ExpectedChildChild (test) ExpectedChildChildJvm (test) Press ... to navigate'"), LINE_MARKER("descr='Overrides function in SimpleParent (test) Press ... to navigate'")!>`foo fun`<!>(n: Int)
    override val <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is overridden in ExpectedChildChild (test) ExpectedChildChildJvm (test) Press ... to navigate'"), LINE_MARKER("descr='Overrides property in SimpleParent (test) Press ... to navigate'")!>`bar fun`<!>: Int
}

class ExpectedChildChild : ExpectedChild() {
    override fun <!LINE_MARKER("descr='Overrides function in ExpectedChild (test) Press ... to navigate'")!>`foo fun`<!>(n: Int) {}
    override val <!LINE_MARKER("descr='Overrides property in ExpectedChild (test) Press ... to navigate'")!>`bar fun`<!>: Int get() = 1
}

class SimpleChild : SimpleParent() {
    override fun <!LINE_MARKER("descr='Overrides function in SimpleParent (test) Press ... to navigate'")!>`foo fun`<!>(n: Int) {}
    override val <!LINE_MARKER("descr='Overrides property in SimpleParent (test) Press ... to navigate'")!>`bar fun`<!>: Int get() = 1
}
