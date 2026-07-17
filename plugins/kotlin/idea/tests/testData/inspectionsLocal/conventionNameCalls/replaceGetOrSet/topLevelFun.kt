// PROBLEM: none
// ERROR: 'operator' modifier is inapplicable on this function: must be a member or an extension function
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
package p

operator fun get(s: String) = s

fun foo() {
    p.<caret>get("x")
}
