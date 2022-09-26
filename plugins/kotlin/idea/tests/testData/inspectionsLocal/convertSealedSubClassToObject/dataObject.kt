// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// FIX: Convert sealed subclass to object

sealed class Sealed

<caret>class SubSealed : Sealed()
