// See: KTIJ-21506

// WITH_STDLIB
fun test(nums: IntArray) {
    nums.withIndex().groupBy({ (_, value) -> <caret> value }) { (idx, _) -> idx }
}

/*
Text: (<highlight>keySelector: (T) -> K</highlight>), Disabled: false, Strikeout: false, Green: false
Text: (<highlight>keySelector: (T) -> K</highlight>, valueTransform: (T) -> V), Disabled: false, Strikeout: false, Green: true
Text_K2: (<highlight>keySelector: (IndexedValue<Int>) -> Int</highlight>), Disabled: false, Strikeout: false, Green: false
Text_K2: (<highlight>keySelector: (IndexedValue<Int>) -> Int</highlight>, valueTransform: (IndexedValue<Int>) -> Int), Disabled: false, Strikeout: false, Green: true
*/