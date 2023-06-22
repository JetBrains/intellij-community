// FIR_COMPARISON
// FIR_IDENTICAL
typealias MyInt = Int

fun foo(n: MyInt) {}

fun test() {
    foo(<caret>
}
// EXIST: {"lookupString":"n =","tailText":" MyInt /* = Int */","icon":"Parameter","allLookupStrings":"n =","itemText":"n ="}