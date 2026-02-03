// PROBLEM: "Use of getter method instead of property access syntax"
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties

fun main() {
    val myFoo = Foo()
    funFunction0(myFoo::<caret>getFoo)
}

fun funFunction0(function: Function0<Int>) {}