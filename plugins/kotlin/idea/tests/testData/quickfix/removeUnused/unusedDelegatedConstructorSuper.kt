// "Safe delete constructor" "false"
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected

open class CtorUsedParent {
    <caret>constructor()
}

class CtorUsedChild : CtorUsedParent {
    constructor() : super()
}