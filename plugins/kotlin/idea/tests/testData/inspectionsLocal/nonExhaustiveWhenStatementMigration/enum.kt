// LANGUAGE_VERSION: 1.6

enum class EnumMode { ON, OFF }

fun enumTest() {
    val x: EnumMode = EnumMode.ON
    w<caret>hen (x) {
        EnumMode.ON -> "ON"
    }
}