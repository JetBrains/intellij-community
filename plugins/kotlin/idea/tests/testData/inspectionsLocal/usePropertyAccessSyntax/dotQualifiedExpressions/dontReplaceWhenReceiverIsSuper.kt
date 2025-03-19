// PROBLEM: none

class KotlinInheritor: Foo() {

    override fun setFoo(x: Int) {
        super.<caret>setFoo(x)
    }
}