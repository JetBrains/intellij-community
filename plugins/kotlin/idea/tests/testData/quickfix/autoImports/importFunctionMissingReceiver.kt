// "Import" "false"

// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Create extension function 'A.Companion.foo'
// ACTION: Create member function 'A.Companion.foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

package import_intention

fun main() {
    A.<caret>foo()
}

class A {

}

class B {
    companion object {
        fun foo() {}
    }
}

class C {
    companion object {
        fun foo() {}
    }
}