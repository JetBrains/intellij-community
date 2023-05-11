// "Make 'String' open" "false"
// ACTION: Add full qualifier
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Inline type parameter
// ACTION: Remove final upper bound
class A<T : String<caret>> {}
