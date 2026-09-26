// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface A
class B : A

class C {
    //comment
    //

    val items: A
        field<caret> = B()
    //one more comment
}