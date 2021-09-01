package sample

public expect interface <!LINE_MARKER("descr='Has actuals in JVM'"), LINE_MARKER("descr='Is subclassed by Base Concrete  Click or press ... to navigate'")!>I<!> {
    public suspend fun <A : Appendable> <!LINE_MARKER("descr='Has actuals in JVM'"), LINE_MARKER("descr='Is overridden in sample.Base'")!>readUTF8LineTo<!>(out: A, limit: Int): Boolean
}
