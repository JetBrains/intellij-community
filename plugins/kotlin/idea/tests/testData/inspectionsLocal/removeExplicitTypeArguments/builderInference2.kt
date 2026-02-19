// PROBLEM: none
// LANGUAGE_VERSION: 1.7
// WITH_STDLIB
data class Foo(val value: Int)

fun a() = buildMap<String, Foo><caret> {
    val found = this["foo"]
    if (found == null) {
        this["foo"] = Foo(42)
    } else {
        this["foo"] = found.copy(value = 1)
    }
}
