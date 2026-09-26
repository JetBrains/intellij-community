// "Create extension property 'E.ONE.foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

enum class E {
    ONE {
        override fun implementMe(): Int {
            return fo<caret>o // unresolved
        }
    };
    abstract fun implementMe(): Int
}