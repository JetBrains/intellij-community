// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C {
    fun Int.fn(): Int = this
}

context(<caret>C)
fun f() {
    // 42.let { it }.fn().let { it }
    /*1*/42/*2*/./*3*/let/*4*/ { /*5*/ it /*6*/ } /*7*/./*8*/fn /*9*/ (/*10*/)/*11*/./*12*/let /*13*/ { /*14*/ it /*15*/ }/*16*/
}
