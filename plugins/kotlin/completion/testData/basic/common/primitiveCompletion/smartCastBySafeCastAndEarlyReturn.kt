// IGNORE_K1
open class Base(val a: Int)
class Class(val b: Int) : Base(0)

fun test(x: Base) {
    x as? Class ?: return
    x.<caret>
}

// EXIST: b