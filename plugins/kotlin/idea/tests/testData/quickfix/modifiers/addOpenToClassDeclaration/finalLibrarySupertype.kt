// "Make 'String' open" "false"
// ERROR: This type is final, so it cannot be inherited from
// K2_AFTER_ERROR: This type is final, so it cannot be extended.
// ACTION: Add full qualifier
class A : String<caret>() {}
