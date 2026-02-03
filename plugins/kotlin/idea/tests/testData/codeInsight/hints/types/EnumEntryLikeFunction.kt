// MODE: property
enum class E { ENTRY;
    companion object {
        fun test(): E = ENTRY
    }
}

val test/*<# : |[E:kotlin.fqn.class]E #>*/ = E.test()