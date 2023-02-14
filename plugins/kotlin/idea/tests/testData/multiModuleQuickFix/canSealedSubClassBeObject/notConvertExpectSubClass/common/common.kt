// "Convert sealed subclass to object" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection
// ACTION: Make internal
// ERROR: Expected class 'Child' has no actual declaration in module testModule_Common

sealed class SealedClass
expect cla<caret>ss Child : SealedClass