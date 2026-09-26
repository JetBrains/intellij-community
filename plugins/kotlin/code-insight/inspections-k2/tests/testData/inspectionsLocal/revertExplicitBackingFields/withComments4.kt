// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
internal class Foo {

    val x: List<Int>
        /*
        very important comment!
        */
        field<caret> = mutableListOf<Int>()
}