// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
enum class SomeEnum {
    <caret>UNUSED;

    companion object {
        val entries = ""
    }
}

fun main() {
    SomeEnum.entries
}
