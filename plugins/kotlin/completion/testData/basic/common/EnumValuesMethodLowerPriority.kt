// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// LANGUAGE_VERSION: 1.8

enum class KotlinEnum {
    ;

    companion object {
        fun values(arg: Boolean) {}
        fun valuezzz() {}
    }
}

fun getValues(): Array<KotlinEnum> {
    return KotlinEnum.v<caret>
}

// WITH_ORDER
// EXIST: { itemText: "values", tailText:"(arg: Boolean)" }
// EXIST: { itemText: "valuezzz", tailText:"()" }
// EXIST: { itemText: "valueOf", tailText:"(value: String)" }
// EXIST: { itemText: "values", tailText:"()" }
