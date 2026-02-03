// "Replace with 'Foo'" "true"
package bar

import foo.Foo

@Deprecated(message = "", ReplaceWith("Foo", imports = "foo.Foo"))
typealias Foo = foo.Foo

fun main() {
    println(<selection><caret></selection>Foo)
}

// IGNORE_K1