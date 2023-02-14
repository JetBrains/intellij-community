package sample

fun booleanTest() {
    val x: Boolean = true
    when (x) {
        true -> "true"
    }
}

enum class EnumMode { ON, OFF }
fun enumTest() {
    val x: EnumMode = EnumMode.ON
    when (x) {
        EnumMode.ON -> "ON"
    }
}

sealed class SealedMode {
    object ON : SealedMode()
    object OFF : SealedMode()
}

fun sealedTest() {
    val x: SealedMode = SealedMode.ON
    when (x) {
        SealedMode.ON -> "ON"
    }
}