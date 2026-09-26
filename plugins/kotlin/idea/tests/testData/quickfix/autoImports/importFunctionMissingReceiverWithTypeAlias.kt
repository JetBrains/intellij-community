// "Import" "false"
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Rename reference
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

package import_intention

typealias D = A


fun main() {
    D.<caret>foo()
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