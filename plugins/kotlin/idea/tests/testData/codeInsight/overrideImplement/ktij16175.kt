// FIR_IDENTICAL

interface Foo {
    val text: String
    val name: String
}

class <caret>Bar(override val text: String, val number: Int) : Foo {

}

// MEMBER: "name: String"