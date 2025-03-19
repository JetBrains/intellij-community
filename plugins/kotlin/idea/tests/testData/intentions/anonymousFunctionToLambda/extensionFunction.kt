// IS_APPLICABLE: false

interface Bar

fun <T, R> foo(receiver: T, block: T.() -> R): R {
    return receiver.block()
}

fun Bar.function2() {
    val builder = fu<caret>n Bar.() {

    }
    foo("") {
        this@function2.builder()
    }
}

// IGNORE_K1