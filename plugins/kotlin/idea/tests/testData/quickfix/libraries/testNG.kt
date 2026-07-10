// "Add 'testng' to classpath" "true"
// ERROR: Unresolved reference: BeforeMethod
// WITH_STDLIB
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

// Do not apply quickfix as platform can't handle open maven download dialog in unit test mode
// APPLY_QUICKFIX: false

package some

abstract class KBase {
    @<caret>BeforeMethod
    fun setUp() {
        throw UnsupportedOperationException()
    }
}