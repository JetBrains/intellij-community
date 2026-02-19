// FIX: Replace with 'forEach'

fun foo(nums: List<Int>) {
    nums.ma<caret>p {
        if (it % 2 == 0) {
            println("even")
            return@map
        } else {
            println("odd")
            return@map
        }
    }
}

// IGNORE_K1
