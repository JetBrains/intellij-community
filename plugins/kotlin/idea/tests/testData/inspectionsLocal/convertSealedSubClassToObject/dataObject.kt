// LANGUAGE_VERSION: 1.8
// FIX: Convert sealed subclass to object

sealed class Sealed

<caret>class SubSealed : Sealed()
