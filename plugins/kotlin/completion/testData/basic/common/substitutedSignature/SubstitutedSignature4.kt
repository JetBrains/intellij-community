interface Trait<T>

fun<T> Trait<T>.extension(t: T): T = t

class Outer : Trait<String> {
    inner class Inner : Trait<Int> {
        fun foo() {
            ext<caret>
        }
    }
}

// IGNORE_K2
// EXIST: { itemText: "extension", tailText: "(t: String) for Trait<T> in <root>", typeText: "String" }
// EXIST: { itemText: "extension", tailText: "(t: Int) for Trait<T> in <root>", typeText: "Int" }
