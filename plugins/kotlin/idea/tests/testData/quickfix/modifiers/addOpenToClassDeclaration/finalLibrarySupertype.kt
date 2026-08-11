// "Make 'String' open" "false"
// K2_AFTER_ERROR: FINAL_SUPERTYPE
// K2_ERROR: FINAL_SUPERTYPE
class A : String<caret>() {}
