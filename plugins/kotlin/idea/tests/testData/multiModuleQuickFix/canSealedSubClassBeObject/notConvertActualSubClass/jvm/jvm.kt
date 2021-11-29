// "Convert sealed sub-class to object" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection
// ACTION: Create test
// ACTION: Generate equals & hashCode by identity
// ACTION: Make internal
// ACTION: Make protected

actual sealed class SealedClass {
    actual cl<caret>ass Child : SealedClass()
}