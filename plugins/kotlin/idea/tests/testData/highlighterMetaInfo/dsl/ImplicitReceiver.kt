// WITH_STDLIB
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package otherPackage

@DslMarker
annotation class TestDsl

@TestDsl
class TestBuilder {
    fun test() {}
}

fun implicitReceiverTest() {
    with(TestBuilder()) { // Should NOT be highlighted
        test() // Should be highlighted
    }

    TestBuilder().apply { // Should NOT be highlighted
        test() // Should be highlighted
    }
}