// "Make 'Nested' public" "true"
// ACTION: Convert parameter to receiver
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Make 'Nested' internal
// ACTION: Make 'Nested' public
// ACTION: Remove parameter 'arg'

class Outer {
    private class Nested
}

class Generic<T>

internal fun foo(<caret>arg: Generic<Outer.Nested>) {}