// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface A
object B : A

val pet: A
    field<caret> = B
