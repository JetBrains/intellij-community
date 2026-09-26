// MODE: all
fun foo() {
    val x/*<# : |[kotlin.Unit:kotlin.fqn.class]Unit #>*/ =
        println("Foo") // indent differs
}