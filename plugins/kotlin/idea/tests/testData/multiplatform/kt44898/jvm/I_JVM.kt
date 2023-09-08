package sample

public actual interface <!LINE_MARKER("descr='Has expects in common module'")!>I<!> {
    public actual suspend fun <A : Appendable> <!LINE_MARKER("descr='Has expects in common module'")!>readUTF8LineTo<!>(out: A, limit: Int): Boolean
}
