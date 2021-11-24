// IS_APPLICABLE: false
// WITH_STDLIB

import kotlin.properties.Delegates

class A {
    val v1: Int by <caret>Delegates.notNull()
}