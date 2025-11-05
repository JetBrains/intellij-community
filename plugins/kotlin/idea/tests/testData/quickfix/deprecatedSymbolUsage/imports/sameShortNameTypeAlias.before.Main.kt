// "Replace with 'Foo'" "true"
package bar

@Deprecated(message = "", ReplaceWith("Foo", imports = "foo.Foo"))
typealias Foo = foo.Foo

fun main() {
    println(Fo<caret>o)
}

// IGNORE_K1