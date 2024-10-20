// MODE: all
fun foo() {
    open class A

    val c = object : A() {}
    val b = A()
    val d/*<# : |(|) -> |[LocalClass.kt:29]A #>*/ = { A() }