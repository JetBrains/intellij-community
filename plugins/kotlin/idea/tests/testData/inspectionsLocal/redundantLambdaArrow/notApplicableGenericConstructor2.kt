// PROBLEM: none
// WITH_STDLIB

fun main() {
    MyClass { it<caret>: String -> 0 }
}

class MyClass<K, V>(
    private val selector: (K) -> V
)