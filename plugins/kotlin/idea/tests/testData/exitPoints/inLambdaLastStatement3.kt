fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it == null) return@foo 1
        if (it == "a") 2 e<caret>lse 0
    }
}