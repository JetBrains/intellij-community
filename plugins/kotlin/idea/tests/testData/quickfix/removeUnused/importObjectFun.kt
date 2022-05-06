// "Safe delete 'MyObj'" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Rename file to MyObj.kt

import MyObj.foo

object <caret>MyObj {
    fun foo() {}
}