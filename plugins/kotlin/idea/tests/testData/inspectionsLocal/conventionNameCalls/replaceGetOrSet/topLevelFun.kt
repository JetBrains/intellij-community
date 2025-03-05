// PROBLEM: none
// K2_ERROR: 'operator' modifier is not applicable to function: must be a member or an extension function.
// ERROR: 'operator' modifier is inapplicable on this function: must be a member or an extension function
package p

operator fun get(s: String) = s

fun foo() {
    p.<caret>get("x")
}
