// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax -XXLanguage:+FullValueClasses
// K2_ERROR: UNRESOLVED_REFERENCE

value object FullValueObject

fun test(o: FullValueObject) {
    (val foo<caret>) = o
}
