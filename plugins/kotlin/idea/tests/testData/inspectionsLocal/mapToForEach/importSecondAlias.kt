// FIX: Replace with 'forEach'

import kotlin.collections.map as foo
import kotlin.collections.map as bar

fun test(nums: List<Int>) {
    nums.ba<caret>r { it }
}
