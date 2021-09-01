// LANGUAGE_VERSION: 1.6

sealed class SealedMode {
    object ON : SealedMode()
    object OFF : SealedMode()
}

fun sealedTest() {
    val x: SealedMode = SealedMode.ON
    wh<caret>en (x) {
        SealedMode.ON -> "ON"
    }
}