// "Import" "true"
// ERROR: For-loop range must have an 'iterator()' method
// WITH_STDLIB

package bar

import foo.Foo

fun foo(start: Foo, end: Foo) {
    for (date in start<caret>..end) {}
}
/* IGNORE_FIR */