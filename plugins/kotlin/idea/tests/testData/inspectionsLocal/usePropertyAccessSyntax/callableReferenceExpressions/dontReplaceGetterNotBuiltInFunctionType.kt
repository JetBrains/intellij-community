// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// PROBLEM: none
import kotlin.reflect.KFunction

fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: KFunction<Int>): Any = foo
