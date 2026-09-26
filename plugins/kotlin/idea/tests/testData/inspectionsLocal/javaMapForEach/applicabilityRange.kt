// PROBLEM: none
// RUNTIME_WITH_FULL_JDK
fun test(map: Map<Int, String>) {
    map.forEach { key, value ->
        foo<caret>(key, value)
    }
}

fun foo(i: Int, s: String) {}