// FIR_COMPARISON
fun <T> foo(f: List<T>.() -> Unit) {}

fun <T> List<T>.extension(t: T?): T? {}

fun test() {
    foo {
        this.exten<caret>
    }
}

// EXIST: {"lookupString":"extension","tailText":"(t: T?) for List<T> in <root>","typeText":"T?","icon":"Function"}
// In K2 when substituted type is error type, unsubstituted type, containing type parameter, is rendered