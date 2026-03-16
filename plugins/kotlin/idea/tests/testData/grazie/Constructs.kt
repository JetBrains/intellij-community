@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")

package ide.language.kotlin

interface A {
    val <TYPO descr="Typo: In word 'typpo'">typpo</TYPO>: Int
    fun <TYPO descr="Typo: In word 'typpo'">typpo</TYPO>(<TYPO descr="Typo: In word 'typpo'">typpo</TYPO>: Int)
}

class B : A {
    // typos are ignored because of `override` keyword
    override val typpo: Int = 0
    override fun typpo(typpo: Int) { }
}

val variableWith<TYPO descr="Typo: In word 'Eror'">Eror</TYPO> = "error"

fun <TYPO descr="Typo: In word 'eror'">eror</TYPO>Function(<TYPO descr="Typo: In word 'eror'">eror</TYPO>: Int) {}

object ObjectWith<TYPO descr="Typo: In word 'Eror'">Eror</TYPO>

class ClassWith<TYPO descr="Typo: In word 'Eror'">Eror</TYPO>