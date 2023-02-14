// "Reorder parameters" "true"
fun println(any: Any) = Unit

fun foo(
    a5: () -> Int = { println(a3<caret>); println(a2); 1 },
    a3: Int = a1,
    a4: Int = a2,
    a6: () -> Int = { println(a4); println(a5); 2 },
    a1: Int = 1,
    a2: Int = 2,
) = Unit

fun main() {
    foo(
        { 1 },
        2
    )
}
