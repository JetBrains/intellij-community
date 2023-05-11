// FIR_COMPARISON
fun <T> foo(f: List<T>.() -> Unit) {}

fun <T> List<T>.extension(t: T?): T? = t

fun test() {
    foo {
        this.exten<caret>
    }
}

// EXIST: {"lookupString":"extension","tailText":"(t: TypeVariable(T)?) for List<T> in <root>","typeText":"TypeVariable(T)?","icon":"Function"}