package seal

sealed class Sealed

data object SubSealed : Sealed() {
    class Nested

    fun internalFunction() {}
}