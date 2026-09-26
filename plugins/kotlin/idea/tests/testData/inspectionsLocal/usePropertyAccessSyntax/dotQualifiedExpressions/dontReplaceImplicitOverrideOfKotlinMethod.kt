// PROBLEM: none

interface KotlinBase {
    fun getName(): String
}

private fun usage(jc: JavaChild) {
    jc.<caret>getName()
}