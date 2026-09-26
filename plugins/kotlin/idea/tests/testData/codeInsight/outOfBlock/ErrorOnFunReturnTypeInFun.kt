// OUT_OF_CODE_BLOCK: FALSE
// ERROR: Return type of 'inheritMe' is not a subtype of the return type of the overridden member 'public open fun inheritMe(p: Int): String defined in JustParent'
// ERROR: Unresolved reference: a
open class JustParent {
    open fun inheritMe(p: Int): String = ""
}

class JustClass : JustParent() {
    // RETURN_TYPE_MISMATCH_ON_OVERRIDE
    override fun inheritMe(p: Int) {
        val q = <caret>
    }
}