// "Create extension property 'E.ONE.foo'" "false"
// ERROR: Unresolved reference: foo

enum class E {
    ONE {
        override fun implementMe(): Int {
            return fo<caret>o // unresolved
        }
    };
    abstract fun implementMe(): Int
}