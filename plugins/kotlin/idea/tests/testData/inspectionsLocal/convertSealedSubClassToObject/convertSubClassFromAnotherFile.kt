// FIX: Convert sealed sub-class to object
// COMPILER_ARGUMENTS: -XXLanguage:+SealedInterfaces -XXLanguage:+AllowSealedInheritorsInDifferentFilesOfSamePackage
<caret>class SubSealed : Sealed()