// COMPILER_ARGUMENTS: -Xcompanion-blocks
// IS_APPLICABLE: false

class Sample {
    operator fun p<caret>lus(p: Int) = p + 1
}

fun use(s: Sample) {
    s + 2
}
