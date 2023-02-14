// FIX: Convert sealed subclass to object

sealed class Sealed

private <caret>class SubSealed : Sealed()