// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// IS_APPLICABLE: false
import kotlin.reflect.KFunction

fun main() {
    suppressUnused(Foo::<caret>getFoo)
}

fun suppressUnused(foo: KFunction<Int>): Any = foo
