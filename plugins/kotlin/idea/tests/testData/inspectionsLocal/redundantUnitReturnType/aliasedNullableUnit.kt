// PROBLEM: none
typealias MyNullableUnit = Unit?

fun test(): <caret>MyNullableUnit {
    return null
}