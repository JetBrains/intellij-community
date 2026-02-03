// FIX: Replace with 'forEach'

fun foo(nums: Iterable<Int>) {
    nums.map { it }.m<caret>ap(fun(it: Int): Unit {
        if (it > 10) return@map
        print(it)
    })
}

// IGNORE_K1
