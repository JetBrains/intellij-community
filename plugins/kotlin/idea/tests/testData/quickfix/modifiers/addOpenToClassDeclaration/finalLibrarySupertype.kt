// "Make 'String' open" "false"
// ERROR: This type is final, so it cannot be inherited from
// ACTION: Add full qualifier
// K2_AFTER_ERROR: FINAL_SUPERTYPE
// K2_ERROR: FINAL_SUPERTYPE
class A : String<caret>() {}
