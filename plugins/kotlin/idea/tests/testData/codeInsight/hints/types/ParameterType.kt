// MODE: parameter
fun <T> T.wrap(lambda: (T) -> T) {}
fun foo() {
    12.wrap { elem/*<# [:  [jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/Primitives.kt:*]Int] #>*/ ->
        elem
    }
}
