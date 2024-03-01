class A {
    companion object {
        val prefixTest = 5
    }
}
val a = prefix<caret>

// ABSENT: prefixTest
// NOTHING_ELSE