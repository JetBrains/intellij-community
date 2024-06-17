// PROBLEM: "Use of getter method instead of property access syntax"
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties

fun main() {
    funFunction1(Foo::<caret>getFoo)
}

fun funFunction1(function: Function1<Foo, Int>) {}