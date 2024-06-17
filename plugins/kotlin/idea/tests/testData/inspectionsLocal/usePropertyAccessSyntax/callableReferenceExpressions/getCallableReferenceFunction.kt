// PROBLEM: "Use of getter method instead of property access syntax"
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
import java.util.function.Function

fun main() {
    funFunction(Foo::<caret>getFoo)
}

fun funFunction(function: Function<Foo, Int>) {}