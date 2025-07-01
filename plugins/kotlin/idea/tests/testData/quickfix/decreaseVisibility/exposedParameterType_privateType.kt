// "Make 'foo' private" "false"
// ACTION: Convert parameter to receiver
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Make 'Nested' internal
// ACTION: Make 'Nested' public
// ACTION: Remove parameter 'arg'
// ERROR: 'internal' function exposes its 'private-in-class' parameter type argument Nested
// ERROR: Cannot access 'Nested': it is private in 'Outer'
// K2_AFTER_ERROR: 'internal' function exposes its 'private-in-class' parameter type argument 'Nested'.
// K2_AFTER_ERROR: Cannot access 'class Nested : Any': it is private in 'Outer'.

class Outer {
    private class Nested
}

class Generic<T>

internal fun foo(<caret>arg: Generic<Outer.Nested>) {}
