fun test() {
    val a = arrayOf(1,2)
    assertEquals(0, a[<caret>0])
}

fun <T> assertEquals(expected: T, actual: T, message: String? = null) {
}

/*
Text: (<highlight>index: Int</highlight>), Disabled: false, Strikeout: false, Green: true
*/