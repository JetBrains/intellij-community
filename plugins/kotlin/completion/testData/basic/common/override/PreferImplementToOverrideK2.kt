interface I {
    fun bbb()
}

open class Base {
    open fun aaa() {}
}

class A : Base(), I {
    overr<caret>
}

// IGNORE_K1
// WITH_ORDER
// EXIST: { itemText: "override fun bbb() {...}" }
// EXIST: { itemText: "override fun aaa() {...}" }
// EXIST: { itemText: "override" }