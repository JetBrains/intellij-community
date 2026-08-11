// "Make 'foo' private" "false"
// ACTION: Convert parameter to receiver
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Make 'Nested' internal
// ACTION: Make 'Nested' public
// ACTION: Remove parameter 'arg'
// ERROR: 'internal' function exposes its 'private-in-class' parameter type argument Nested
// ERROR: Cannot access 'Nested': it is private in 'Outer'
// K2_AFTER_ERROR: EXPOSED_PARAMETER_TYPE
// K2_AFTER_ERROR: INVISIBLE_REFERENCE
// K2_ERROR: EXPOSED_PARAMETER_TYPE
// K2_ERROR: INVISIBLE_REFERENCE

class Outer {
    private class Nested
}

class Generic<T>

internal fun foo(<caret>arg: Generic<Outer.Nested>) {}
