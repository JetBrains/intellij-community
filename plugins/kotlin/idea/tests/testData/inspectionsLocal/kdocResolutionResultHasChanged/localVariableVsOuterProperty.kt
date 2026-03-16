// REGISTRY: kotlin.analysis.experimentalKDocResolution true
// FIX: none

interface A {
    val pp: Int

    fun foo() {
        val pp = 6

        /** [p<caret>p] */
        val x = 5
    }
}