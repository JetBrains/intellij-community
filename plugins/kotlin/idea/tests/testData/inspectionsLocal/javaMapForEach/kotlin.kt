// PROBLEM: none
// WITH_STDLIB
fun test(map: Map<Int, String>) {
    map.<caret>forEach { (key, value) ->
        foo(key, value)
    }
}

fun foo(i: Int, s: String) {}