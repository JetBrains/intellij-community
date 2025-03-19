// MODE: all
fun foo() {
    open class A

    val c = object : A() {}
    val b = A()
    val d/*<# : |(|) |->| |[foo.A:kotlin.fqn.class]A #>*/ = { A() }