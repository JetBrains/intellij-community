// "Add 'JUnit4' to classpath" "true"
// ERROR: Unresolved reference: Before
// ERROR: Unresolved reference: junit
// WITH_STDLIB
// K2_AFTER_ERROR: UNRESOLVED_IMPORT
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_IMPORT
// K2_ERROR: UNRESOLVED_REFERENCE

// Do not apply quickfix as platform can't handle open maven download dialog in unit test mode
// APPLY_QUICKFIX: false

package some

import org.<caret>junit.Before

open class KBase {
    @Before
    fun setUp() {
        throw UnsupportedOperationException()
    }
}