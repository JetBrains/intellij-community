// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
internal class Foo {

    val x: List<Int>
        // comment
        field<caret> = mutableListOf<Int>()
}