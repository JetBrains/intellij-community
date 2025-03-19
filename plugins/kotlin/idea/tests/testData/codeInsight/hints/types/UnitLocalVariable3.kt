// MODE: local_variable
fun foo() {
    val x/*<# : |[kotlin.Unit:kotlin.fqn.class]Unit #>*/ = println("Foo")
}
