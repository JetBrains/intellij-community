// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: Recursive type alias in expansion.
typealias RecursiveTATopLevelB = <caret>List<RecursiveTATopLevelB>
