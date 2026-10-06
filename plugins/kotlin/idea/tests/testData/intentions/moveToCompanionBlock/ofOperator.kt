// COMPILER_ARGUMENTS: -Xcompanion-blocks -Xcollection-literals
// IS_APPLICABLE: false
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
// K2_ERROR: UNRESOLVED_COLLECTION_LITERAL

class Sample {
    operator fun of<caret>(vararg p: Int) = Sample()
}

fun use(s: Sample) {
    val v: Sample = [1, 2, 3]
}
