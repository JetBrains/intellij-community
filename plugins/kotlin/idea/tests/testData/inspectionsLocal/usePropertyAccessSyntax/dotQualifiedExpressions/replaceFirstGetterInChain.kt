// FIX: Use property access syntax

fun main() {
    J().<caret>getX().doSth()
}

fun Int.doSth() {}