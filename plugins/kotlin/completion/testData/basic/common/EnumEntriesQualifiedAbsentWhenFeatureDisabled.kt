// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries

enum class MyEnum {
    ;
    companion object {
        val entries: String = "custom property"
    }
}
fun foo() {
    MyEnum.entr<caret>
}

// EXIST: { itemText: "entries", typeText: "String" }
// NOTHING_ELSE
