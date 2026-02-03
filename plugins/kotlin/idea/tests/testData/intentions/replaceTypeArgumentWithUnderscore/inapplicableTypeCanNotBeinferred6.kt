// IS_APPLICABLE: false

inline fun <T, reified K : Collection<T>> foo() = ""

fun main() {
    foo<String, List<<caret>String>>()
}