// "Generate equals & hashCode by identity" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.CanSealedSubClassBeObjectInspection
// ACTION: Convert sealed subclass to object
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make private
// IGNORE_K2
sealed class Parent
cla<caret>ss Child : Parent()