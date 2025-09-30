// PROBLEM: none

fun foo(nums: List<Int>) {
    nums.ma<caret>p { it }.map { it }
}

// IGNORE_K1
