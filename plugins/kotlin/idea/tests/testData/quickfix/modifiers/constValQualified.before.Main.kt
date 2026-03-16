// "Make 'NAME' 'const'" "true"
// ERROR: Only 'const val' can be used in constant expressions

package constVal

object ObjName {
    val NAME = 42
}

annotation class Fancy(val param: Int)

@Fancy(<caret>ObjName.NAME) class D
