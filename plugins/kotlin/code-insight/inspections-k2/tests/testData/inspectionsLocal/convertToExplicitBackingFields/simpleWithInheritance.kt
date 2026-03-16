// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface A
class B : A

class C {
    //comment
    //

    private val _items = B()
    val items: A get() = _items<caret>
    //one more comment
}