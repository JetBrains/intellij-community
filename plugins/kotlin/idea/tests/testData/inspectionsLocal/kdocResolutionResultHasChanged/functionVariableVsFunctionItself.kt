// REGISTRY: kotlin.analysis.experimentalKDocResolution true
// FIX: none

fun foo(t: String) {
    val foo = "hi"

    /**
     * [fo<caret>o]
     */
    fun boo() { }
}