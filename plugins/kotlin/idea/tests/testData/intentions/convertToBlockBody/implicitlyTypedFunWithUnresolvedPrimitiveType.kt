// IS_APPLICABLE: false
// ERROR: Type checking has run into a recursive problem. Easiest workaround: specify types of your declarations explicitly
// K2_ERROR: Type checking has run into a recursive problem. Easiest workaround: specify the types of your declarations explicitly.
fun foo() = 42 + foo()<caret>