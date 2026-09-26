// "Make 'x' public" "true"

open class Base {
    protected open fun x() {}
}

class First : Base() {
    override fun x() {}
}

class Second(f: First) {
    val y = f.<caret>x()
}
