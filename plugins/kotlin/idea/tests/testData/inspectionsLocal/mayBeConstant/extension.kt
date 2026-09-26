// PROBLEM: none
// WITH_STDLIB
// ERROR: Extension property cannot be initialized because it has no backing field
// K2_ERROR: EXTENSION_PROPERTY_WITH_BACKING_FIELD

val Int.<caret>foo: Int = 42