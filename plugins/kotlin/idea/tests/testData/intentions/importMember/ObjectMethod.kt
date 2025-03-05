// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'kotlin.properties.Delegates.notNull'"
// WITH_STDLIB

import kotlin.properties.Delegates

class A {
    val v1: Int by Delegates.notNull()
    val v2: Char by Delegates.notNull<caret>()
}