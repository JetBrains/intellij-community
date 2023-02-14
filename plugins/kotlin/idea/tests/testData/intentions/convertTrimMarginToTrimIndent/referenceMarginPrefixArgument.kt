// IS_APPLICABLE: false
// WITH_STDLIB
fun test(marginPrefix: String) {
    val x =
        """
                |a
                |b
            """.<caret>trimMargin(marginPrefix)
}