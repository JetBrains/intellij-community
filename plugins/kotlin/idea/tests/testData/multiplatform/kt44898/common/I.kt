package sample

public expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by Base (sample) Concrete (sample) Press ... to navigate'")!>I<!> {
    public suspend fun <A : Appendable> <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is overridden in Base (sample) Press ... to navigate'")!>readUTF8LineTo<!>(out: A, limit: Int): Boolean
}
