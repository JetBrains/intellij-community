// PRIORITY: LOW
package one.two.three

class Foo {
    class Bar {
        val x: () -> Bar = ::Bar<caret>
    }
}