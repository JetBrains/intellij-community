// "Make 'User' internal" "true"
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Make 'InternalString' public
// ACTION: Make 'User' internal
// ACTION: Make 'User' private

internal open class InternalString

class User<T : <caret>User<T, InternalString>, R>