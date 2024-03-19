// FIX: Use property access syntax
// LANGUAGE_VERSION: 2.1

fun main() {
    call(Boo<Int>::<caret>getBoo)
}

fun call(f: Function1<Boo<Int>, Int>) {}