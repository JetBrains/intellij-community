// MODE: parameter
fun <T> T.wrap(lambda: (T) -> T) {}
fun foo() {
    12.wrap { elem<# [:  [jar://kotlin-stdlib-sources.jar!/kotlin/Primitives.kt:28700]Int] #> ->
        elem
    }
}
