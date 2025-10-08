// FIX: Replace with 'forEach'

fun foo(nums: Iterable<Int>) {
    nums.m<caret>ap { println(it) }
}

// IGNORE_K1
