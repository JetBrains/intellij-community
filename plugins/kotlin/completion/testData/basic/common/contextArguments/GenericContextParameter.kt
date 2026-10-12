// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: T)
fun <T> fooGeneric(value: T) {}

context(_: T)
fun <T : CharSequence> fooBounded(value: T) {}

context(_: T)
fun <T : Number> fooNumber(value: T) {}

fun test() {
    with("") {
        foo<caret>
    }
}

// EXIST: fooGeneric
// EXIST: fooBounded
// ABSENT: fooNumber
