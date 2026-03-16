// FIR_COMPARISON
// FIR_IDENTICAL
interface SomeClass

fun foo(p: SomeClass) {

}

fun test(
    somePrefixMatchingNameWithCorrectType: SomeClass,
    somePrefixMatchingName: Int, // this name is a proper prefix of the one above, so the liftShorter weigher used to lift it to the top
    somePrefixOtherVariableWithCorrectType: SomeClass,
) {
    foo(somePrefix<caret>)
}

// ORDER: somePrefixMatchingNameWithCorrectType
// ORDER: somePrefixOtherVariableWithCorrectType
// ORDER: somePrefixMatchingName
// IGNORE_K1
