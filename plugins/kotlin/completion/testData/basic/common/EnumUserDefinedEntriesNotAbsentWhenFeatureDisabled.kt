// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries

enum class MyEnum(val entries: String) {
    ONE("HELLO")
}

fun foo() {
    MyEnum.ONE.entr<caret>
}

// EXIST: { itemText: "entries", typeText: "String" }
// NOTHING_ELSE
