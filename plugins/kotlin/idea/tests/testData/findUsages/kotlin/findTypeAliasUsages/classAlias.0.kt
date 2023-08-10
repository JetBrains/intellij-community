// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtTypeAlias
// OPTIONS: usages

open class OOO {

    open fun opFun() {}

    companion object {
        const val CONST = ""
    }

}
typealias Alias<caret> = OOO

class Child : Alias() {

    override fun opFun() {
        super<Alias>.opFun() //not found in K2, see KTIJ-26095
    }
}

class Child2 : OOO()

class WithGeneric<T>

fun f2(par: Alias, par2: OOO) : Alias {

    val a: Alias

    a = Alias() //not found in K2, see KTIJ-26095

    val b = WithGeneric<Alias>() //not found in K2, see KTIJ-26095
    val b2 = WithGeneric<OOO>()

    Alias.CONST //not found in K1, found in K2
    OOO.CONST

    if (par2 is Alias) {}
    if (par2 is OOO) {}

    val c = par2 as Alias
    val c2 = par2 as OOO

    return c
}