// MODE: all
fun foo() {
    val x/*<# [:  [jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/Unit.kt:*]Unit] #>*/ =
        println("Foo") // indent differs
}