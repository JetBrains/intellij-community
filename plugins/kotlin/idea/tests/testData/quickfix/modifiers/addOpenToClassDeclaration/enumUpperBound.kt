// "Make 'E' open" "false"
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
enum class E {}
class A<T : E<caret>> {}
