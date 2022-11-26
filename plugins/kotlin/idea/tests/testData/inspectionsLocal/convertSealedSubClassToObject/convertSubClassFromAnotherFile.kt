// FIX: Convert sealed subclass to object
// COMPILER_ARGUMENTS: -XXLanguage:+SealedInterfaces -XXLanguage:+AllowSealedInheritorsInDifferentFilesOfSamePackage
<caret>class SubSealed : Sealed()