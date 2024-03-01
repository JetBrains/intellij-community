// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries

enum class MyEnum {
    ;

    init {
        entr<caret>
    }
    companion object {
        val entries: String = "custom property"
    }
}

// EXIST: { itemText: "entries", typeText: "String" }
// ABSENT: { itemText: "entries", typeText: "EnumEntries<T>" }
