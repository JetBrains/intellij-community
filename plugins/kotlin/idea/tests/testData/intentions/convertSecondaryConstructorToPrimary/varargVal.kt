// WITH_STDLIB

class VarargVal {
    val param: Array<out String>

    constructor<caret>(vararg param: String) {
        this.param = param
    }
}