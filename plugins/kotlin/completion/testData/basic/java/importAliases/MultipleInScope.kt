import Foo as Bar
import Foo as Baz

private class Foo

fun foo(): Ba<caret> { }

// EXIST: Bar
// EXIST: Baz
// IGNORE_K2