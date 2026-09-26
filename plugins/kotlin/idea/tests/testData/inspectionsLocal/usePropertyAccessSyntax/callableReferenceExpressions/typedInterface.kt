// FIX: Use property access syntax
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties

fun main() {
    call(Boo<Int>::<caret>getBoo)
}

fun call(f: Function1<Boo<Int>, Int>) {}