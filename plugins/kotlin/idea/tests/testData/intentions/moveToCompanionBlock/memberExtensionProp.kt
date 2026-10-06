// COMPILER_ARGUMENTS: -Xcompanion-blocks
// IS_APPLICABLE: false

class Sample {
    var Int.cbPr<caret>opL: Int
        get() = this
        set(value) {}
}

fun Sample.test() {
    5.cbPropL
}
