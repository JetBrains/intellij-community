// LANGUAGE_VERSION: 1.8
// FIX: Convert sealed sub-class to object

sealed class Sealed

<caret>class SubSealed : Sealed()
