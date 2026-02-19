// MODE: property
enum class E {
    ENTRY;
    companion object {
        val test: E = ENTRY
    }
}

val test/*<# : |[E:kotlin.fqn.class]E #>*/ = E.test