// WITH_LIBRARY: javaCls
fun foo() {
    val lib = MyLibrary()
    lib.foo(<caret>/* p0 = */ 1)
}