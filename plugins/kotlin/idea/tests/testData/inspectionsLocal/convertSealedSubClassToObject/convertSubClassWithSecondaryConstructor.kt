// FIX: Convert sealed subclass to object
// WITH_STDLIB

sealed class Sealed

<caret>class SubSealed : Sealed {
    constructor() {
        println("init")
    }
}