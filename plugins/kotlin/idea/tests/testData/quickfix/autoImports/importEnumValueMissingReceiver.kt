// "Import" "false"

// ACTION: Create object 'foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

package import_intention

fun main() {
    A.<caret>foo
}

class A {

}

object ScopeObject {
    enum class MyEnum { foo }
}
