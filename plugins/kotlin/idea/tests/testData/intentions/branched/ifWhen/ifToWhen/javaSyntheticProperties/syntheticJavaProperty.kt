// WITH_STDLIB
class K : JavaClass() {
    override fun getX(): JavaClass.X = JavaClass.X.A

    fun foo() {
        i<caret>f (x == JavaClass.X.A) println(1)
        else println(2)
    }
}

