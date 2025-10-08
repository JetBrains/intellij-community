// PROBLEM: none

fun foo(nums: List<Int>) {
    consume(nums.m<caret>ap { it })
}

fun consume(map: List<Int>) {}

// IGNORE_K1
