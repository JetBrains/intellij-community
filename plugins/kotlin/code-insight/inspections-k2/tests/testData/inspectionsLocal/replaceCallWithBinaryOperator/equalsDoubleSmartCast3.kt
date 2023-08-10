// PROBLEM: none

fun test(a: Any, b: Any) =
    a is Int && b is Double && a.<caret>equals(b)