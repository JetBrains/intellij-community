// IS_APPLICABLE: false
// ERROR: 'infix' modifier is inapplicable on this function: must be a member or an extension function
// K2_ERROR: 'infix' modifier is inapplicable to this function.
infix fun id(s: String) = s
val x = <caret>id("0").get(0)
