// "Add 'JUnit4' to classpath" "true"
// ERROR: Unresolved reference: Before
// ERROR: Unresolved reference: junit
// WITH_RUNTIME

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