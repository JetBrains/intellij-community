// MODE: property
enum class E {
    ENTRY;
    companion object {
        val test: E = ENTRY
    }
}

val test<# [:  [temp:///src/KotlinReferencesTypeHintsProvider.kt:0]E] #> = E.test
            