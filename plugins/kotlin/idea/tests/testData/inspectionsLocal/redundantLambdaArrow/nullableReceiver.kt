// PROBLEM: Redundant lambda arrow
// WITH_STDLIB

fun test(list: List<String>?) = list?.map { <caret>it -> it.length }
