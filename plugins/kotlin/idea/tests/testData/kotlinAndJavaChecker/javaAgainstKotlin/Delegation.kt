interface I {
    var s: String
}

class IImpl : I {
    override var s: String = ""
}

abstract class C : I by IImpl()