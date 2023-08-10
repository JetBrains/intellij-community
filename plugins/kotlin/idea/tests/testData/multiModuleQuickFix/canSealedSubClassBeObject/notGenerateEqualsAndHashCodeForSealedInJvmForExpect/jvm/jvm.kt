// "Generate equals & hashCode by identity" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make protected
// ERROR: Expected class 'SealedClass' has no actual declaration in module testModule_JVM for JVM

expect sealed class SealedClass {
    clas<caret>s Child : SealedClass
}