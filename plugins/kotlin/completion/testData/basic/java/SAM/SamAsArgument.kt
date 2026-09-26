fun foo(a: Runnable) {}

fun test() {
    foo(<caret>)
}

// EXIST: {"lookupString":"Runnable","itemText":"Runnable","tailText":" {...} (function: () -> Unit) (java.lang)","typeText":"Runnable"}
