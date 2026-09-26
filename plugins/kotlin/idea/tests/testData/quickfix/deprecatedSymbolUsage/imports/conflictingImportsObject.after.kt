// "Replace with 'Foo'" "true"

import foo.*

fun foo() {
    <selection><caret></selection>foo.compat.Foo.toString()
    // As a test data to avoid ambiguities after applying the intention
    Foo.hashCode()
}
