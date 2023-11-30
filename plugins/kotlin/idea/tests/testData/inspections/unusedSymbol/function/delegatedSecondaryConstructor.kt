class CtorUsedByOtherCtor {
    constructor()

    constructor(p: String): this() {
        println(p)
    }
}

open class CtorUsedParent {
    constructor()
}

class CtorUsedChild : CtorUsedParent {
    constructor() : super()
}

@test.anno.EntryPoint
fun use(): Int {
    val first = CtorUsedByOtherCtor("")
    val second = CtorUsedChild(true)
    return first.hashCode() + second.hashCode()
}