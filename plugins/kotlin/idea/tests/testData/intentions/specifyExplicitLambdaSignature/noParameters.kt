// WITH_STDLIB

fun bar() {}

fun foo() {
    run <caret>{
        bar()
    }
}