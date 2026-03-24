// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters

interface I<T>

context(_: I<T>)
fun <T> foo(f: () -> T) {}

context(_: I<Int>, _: I<String>)
fun baz() {
    foo<Str<caret>ing> { "1" }
}

// IGNORE_K1