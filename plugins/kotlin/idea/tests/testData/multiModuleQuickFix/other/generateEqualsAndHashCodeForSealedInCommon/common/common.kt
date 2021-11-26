// "Generate equals & hashCode by identity" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection
// ACTION: Convert sealed sub-class to object
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make private

sealed class Parent
cla<caret>ss Child : Parent()