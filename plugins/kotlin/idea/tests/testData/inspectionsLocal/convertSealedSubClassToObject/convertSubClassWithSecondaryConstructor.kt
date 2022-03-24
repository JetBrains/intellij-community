// FIX: Convert sealed sub-class to object
// WITH_STDLIB

sealed class Sealed

<caret>class SubSealed : Sealed() {
    constructor() {
        println("init")
    }
}