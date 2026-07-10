// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: RECURSIVE_TYPEALIAS_EXPANSION
typealias RecursiveTATopLevelB = <caret>List<RecursiveTATopLevelB>
