// "Add 'testng' to classpath" "true"
// ERROR: Unresolved reference: BeforeMethod
// WITH_RUNTIME

// Do not apply quickfix as platform can't handle open maven download dialog in unit test mode
// APPLY_QUICKFIX: false

package some

abstract class KBase {
    @<caret>BeforeMethod
    fun setUp() {
        throw UnsupportedOperationException()
    }
}