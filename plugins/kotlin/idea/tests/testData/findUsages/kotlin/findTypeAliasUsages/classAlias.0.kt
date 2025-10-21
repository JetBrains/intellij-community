// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtTypeAlias
// OPTIONS: usages

open class OOO {

    open fun opFun() {}

    companion object {
        const val CONST = ""

        operator fun invoke(i: Int) {}
    }

}
typealias Alias<caret> = OOO

class Child : Alias() {

    override fun opFun() {
        super<Alias>.opFun()
    }
}

class Child2 : OOO()

class WithGeneric<T>

fun f2(par: Alias, par2: OOO) : Alias {

    val a: Alias

    a = Alias()

    val b = WithGeneric<Alias>()
    val b2 = WithGeneric<OOO>()

    Alias.CONST //not found in K1, found in K2
    OOO.CONST

    Alias(10)
    OOO(10)

    if (par2 is Alias) {}
    if (par2 is OOO) {}

    val c = par2 as Alias
    val c2 = par2 as OOO

    return c
}