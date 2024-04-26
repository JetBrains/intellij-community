// PROBLEM: "Use of getter method instead of property access syntax"
// LANGUAGE_VERSION: 2.1

fun main() {
    funFunction1(Foo::<caret>getFoo)
}

fun funFunction1(function: Function1<Foo, Int>) {}