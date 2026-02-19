// PROBLEM: none

fun foo(nums: List<Int>) {
    nums.m<caret>ap {
        if (it % 2 == 0) {
            return@map "even"
        } else {
            return@map "odd"
        }
    }
}

// IGNORE_K1
