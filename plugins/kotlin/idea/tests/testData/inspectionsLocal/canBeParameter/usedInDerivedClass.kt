// FIX: Remove 'val' from parameter
open class Base(open <caret>val x: Int) {
    val y = x
}

class Derived(y: Int) : Base(y)
