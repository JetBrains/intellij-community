// "Make 'String' open" "false"
// ERROR: This type is final, so it cannot be inherited from
// ACTION: Add full qualifier
class A : String<caret>() {}
