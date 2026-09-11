// FIX: Remove 'val' from parameter

class Wrapper(vararg <caret>val x: Int) {
    val y = x
}

