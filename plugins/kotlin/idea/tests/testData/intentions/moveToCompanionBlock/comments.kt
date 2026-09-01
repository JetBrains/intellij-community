// COMPILER_ARGUMENTS: -Xcompanion-blocks

/**
* 1
*/
class Foo { // 2
    /**
    * 3
    */
    fun f<caret>oo(/* 4 */ param: Int) { // 5
        // 6
        println("Bar") // 7
    }
}
