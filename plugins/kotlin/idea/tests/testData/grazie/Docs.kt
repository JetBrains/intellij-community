@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")

package ide.language.kotlin

/**
 * A group of *members*.
 *
 * This class has no useful logic; it's just a documentation example.
 *
 * @param T the type of member in this group.
 * @property name the name of this group.
 * @constructor Creates an empty group.
 */
class ExampleClassWithNoTypos<T>(val name: String) {
    /**
     * Adds a [member] to this group.
     * @return the new size of the group.
     */
    fun goodFunction(member: T): Int {
        return 1 // no error comment
    }
}

/**
 * It is <warning descr="EN_A_VS_AN">an</warning> friend of human
 *
 * <warning descr="PLURAL_VERB_AFTER_THIS">This guy have</warning> no useful logic; it's just a documentation example.
 *
 * @param T the <warning descr="KIND_OF_A">type of a</warning> <TYPO descr="Typo: In word 'membr'">membr</TYPO> in this group.
 * @property name the <warning descr="COMMA_WHICH">name which</warning> group
 * @constructor Creates an empty group.
 */
class ExampleClassWithTypos<T>(val name: String) {
    /**
     * <warning descr="UPPERCASE_SENTENCE_START">it</warning> <warning descr="IT_VBZ">add</warning> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>.
     *
     * @return the new size of <warning descr="DT_DT">a the</warning> group.
     */
    fun badFunction(member: T): Int {
        return 1 // It <warning descr="IT_VBZ">are</warning> <TYPO descr="Typo: In word 'eror'">eror</TYPO> comment
    }

    /**
     * @param name1 <warning descr="PLURAL_VERB_AFTER_THIS">This guy have</warning> no useful logic
     * @param name2 This guy
     * @return have no useful logic
     */
    fun withParam(name1: T, name2: T) {}
}

// Just some
// text here

// just <warning descr="EN_A_VS_AN">an</warning> text here

/**
 * В коробке лежало <warning descr="Sklonenije_NUM_NN">пять карандаша</warning>.
 * А <warning descr="grammar_vse_li_noun">все ли ошибка</warning> найдены?
 * Это случилось <warning descr="INVALID_DATE">31 ноября</warning> 2014 г.
 * За весь вечер она <warning descr="ne_proronila_ni">не проронила и слово</warning>.
 * Собрание состоится в <warning descr="RU_COMPOUNDS">конференц зале</warning>.
 * <warning descr="WORD_REPEAT_RULE">Он он</warning> ошибка.
 */
class ForMultiLanguageSupport {
    // Er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um ganz <warning descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</warning>.
    // Das ist <warning descr="FUEHR_FUER">führ</warning> Dich!
    // Das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <warning descr="MANNSTUNDE">Mannstunden</warning>.
}

/**
 * Returns `an true` if expression is part of when condition expression that looks like
 * ```
 * when {
 * a && b -> ...
 * a && !b -> ...
 * }
 * ```
 * * This is <warning descr="EN_A_VS_AN">a</warning> error.
 * ```
 * An non-checked code fragment
 * ```
 */