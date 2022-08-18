// "Safe delete constructor" "false"
// ACTION: Make internal
// ACTION: Make private

class CtorUsedByOtherCtor {
    <caret>constructor()

    constructor(p: String): this()
}
