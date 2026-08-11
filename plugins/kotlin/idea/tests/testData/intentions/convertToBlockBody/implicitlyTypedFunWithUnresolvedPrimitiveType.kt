// IS_APPLICABLE: false
// ERROR: Type checking has run into a recursive problem. Easiest workaround: specify types of your declarations explicitly
// K2_ERROR: TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM
fun foo() = 42 + foo()<caret>