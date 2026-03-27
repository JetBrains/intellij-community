// "Import class 'Test'" "true"
// IGNORE_K1
package b

import testing.Test

object Test

class FunkyTest {
    @Test<selection><caret></selection>
    fun f() {
    }
}
