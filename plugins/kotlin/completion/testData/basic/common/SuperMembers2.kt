interface I {
    fun abstractFun()
    val abstractVal: Int
    fun nonAbstractFun(){}
}

class A : I {
    override fun abstractFun() {
        super.<caret>
    }
}

// IGNORE_K2
// ABSENT: abstractFun
// ABSENT: abstractVal
// EXIST: { itemText: "nonAbstractFun", attributes: "bold" }
// EXIST: { itemText: "equals", attributes: "bold" }
// EXIST: { itemText: "hashCode", attributes: "bold" }
