// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// LANGUAGE_VERSION: 1.8

fun foo() {
    JavaEnum.v<caret>
}

// WITH_ORDER
// EXIST: { itemText: "valueOf", tailText:"(value: String)" }
// EXIST: { itemText: "values", tailText:"(arg: Boolean)" }
// EXIST: { itemText: "valuezzz", tailText:"()" }
// EXIST: { itemText: "values", tailText:"()" }
