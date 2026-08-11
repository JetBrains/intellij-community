// AFTER_ERROR: Function '<no name provided>' must have a body
// K2_AFTER_ERROR: NON_MEMBER_FUNCTION_NO_BODY
fun a(<caret>
    a: Int, b: Any = fun(a: Int,),) {

}