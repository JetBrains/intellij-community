// IGNORE_K2
class Name {
    companion object {
        fun create(): Name = Name()
    }
}

fun foo(name: Name){}

fun bar() {
    val v: (Name) -> Unit = { foo(<caret>) }
}

// ORDER: it
// ORDER: Name
// ORDER: create
