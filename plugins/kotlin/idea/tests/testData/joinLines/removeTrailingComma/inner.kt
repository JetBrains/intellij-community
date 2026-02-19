// AFTER_ERROR: Function '<no name provided>' must have a body
// K2_AFTER_ERROR: Function 'anonymous' must have a body.
fun a(<caret>
    a: Int, b: Any = fun(a: Int,),) {

}