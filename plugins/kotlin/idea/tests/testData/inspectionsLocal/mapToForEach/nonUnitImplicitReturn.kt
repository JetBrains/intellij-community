// FIX: Replace with 'forEach'

fun foo(nums: List<Int>) {
    nums.m<caret>ap {
        if (it % 2 == 0) {
            "even"
        } else {
            "odd"
        }
    }
}

// IGNORE_K1
