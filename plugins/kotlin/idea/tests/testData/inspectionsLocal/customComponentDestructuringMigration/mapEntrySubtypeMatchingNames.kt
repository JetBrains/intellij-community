// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

class MyEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>

fun test(entry: MyEntry<Int, String>) {
    val <caret>(key, value) = entry
}
