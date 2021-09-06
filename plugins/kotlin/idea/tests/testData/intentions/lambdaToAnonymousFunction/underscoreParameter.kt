// IS_APPLICABLE: true
fun foo(f: (Int, String, Int) -> String) {}

fun test() {
    foo <caret>{ _, _, i -> "" }
}