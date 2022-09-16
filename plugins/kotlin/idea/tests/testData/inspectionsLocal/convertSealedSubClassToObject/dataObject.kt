// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// FIX: Convert sealed sub-class to object

sealed class Sealed

<caret>class SubSealed : Sealed()
