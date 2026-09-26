// WITH_STDLIB
// FIX: none
fun test(x: Int) {
    x.run {<caret> String::length }
}
