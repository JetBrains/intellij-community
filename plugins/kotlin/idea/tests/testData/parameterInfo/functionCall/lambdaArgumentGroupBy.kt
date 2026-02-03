// See: KTIJ-21506

// WITH_STDLIB
fun test(nums: IntArray) {
    nums.withIndex().groupBy({ (_, value) -> <caret> value }) { (idx, _) -> idx }
}
