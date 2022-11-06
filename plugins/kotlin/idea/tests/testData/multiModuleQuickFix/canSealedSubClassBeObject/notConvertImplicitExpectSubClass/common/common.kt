// "Convert sealed subclass to object" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make protected
// ERROR: Expected class 'SealedClass' has no actual declaration in module testModule_Common

expect sealed class SealedClass {
    cla<caret>ss Child : SealedClass
}