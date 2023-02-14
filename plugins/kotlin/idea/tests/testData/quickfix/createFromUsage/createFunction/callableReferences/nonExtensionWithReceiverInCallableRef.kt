// "Create extension function 'Int.foo'" "true"
// WITH_STDLIB
fun <T, U> T.map(f: (T) -> U) = f(this)

fun consume(s: String) {}

fun test() {
    consume(1.map(Int::<caret>foo))
}