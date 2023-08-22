// "Remove redundant 'if' statement" "false"
// ACTION: Add braces to 'if' statement
// ACTION: Add braces to all 'if' statements
// ACTION: Invert 'if' condition
// ACTION: Replace 'if' with 'when'

fun bar(p: Int) {
    var v1 = false
    var v2 = false
    <caret>if (p > 0) v2 = true else v1 = false
}

// IGNORE_FIR
// Note: K2 does not support all of inspections, intentions, and quickfixes yet. When enabling K2,
// this test fails because the last two actions are missing at this moment. After implementing all
// inspections/intentions/quickfixes based on K2, we should remove the above "IGNORE_FIR" directive.