// PROBLEM: none

fun foo(nums: Sequence<Int>) {
    nums.m<caret>ap { println(it) }
}

fun main() {
    foo(sequenceOf(1, 2, 3))
}

// IGNORE_K1
