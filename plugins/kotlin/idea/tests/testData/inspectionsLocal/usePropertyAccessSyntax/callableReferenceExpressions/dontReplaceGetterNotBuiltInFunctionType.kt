// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// PROBLEM: none
// LANGUAGE_VERSION: 2.1
import kotlin.reflect.KFunction

fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: KFunction<Int>): Any = foo
