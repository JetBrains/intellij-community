// "Replace with 'Foo'" "true"

import foo.*

fun foo() {
    Fo<caret>o.toString()
    // As a test data to avoid ambiguities after applying the intention
    Foo.hashCode()
}