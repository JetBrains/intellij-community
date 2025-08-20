@file:Suppress("unused", "MayBeConstant", "UNUSED_PARAMETER")

package ide.language.kotlin

/**
 * A group of *members*.
 *
 * This class has no useful logic; it's just a documentation example.
 *
 * @param T the type of member in this group.
 * @property name the name of this group. And another sentence.
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
 * It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
 *
 * @param T the <GRAMMAR_ERROR descr="KIND_OF_A">type of a</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'membr'">membr</TYPO> in this group.
 * @property name the <GRAMMAR_ERROR descr="COMMA_WHICH">name which</GRAMMAR_ERROR> group. <GRAMMAR_ERROR descr="UPPERCASE_SENTENCE_START">and</GRAMMAR_ERROR> another sentence.
 * @constructor Creates an empty group.
 */
class ExampleClassWithTypos<T>(val name: String) {
    /**
     * <GRAMMAR_ERROR descr="UPPERCASE_SENTENCE_START">it</GRAMMAR_ERROR> <GRAMMAR_ERROR descr="IT_VBZ">add</GRAMMAR_ERROR> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>. And here are some correct English words to make the language detector work.
     *
     * @return the new size of <GRAMMAR_ERROR descr="DT_DT">a the</GRAMMAR_ERROR> group.
     */
    fun badFunction(member: T): Int {
        return 1 // It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'eror'">eror</TYPO> comment. And here are some correct English words to make the language detector work.
    }

    /**
     * @param name1 It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
     * @param name2 It is an
     * @return friend of human
     */
    fun withParam(name1: T, name2: T) {}
}

// Just some
// text here

// just <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> text here

/**
 * В коробке лежало <GRAMMAR_ERROR descr="Sklonenije_NUM_NN">пять карандаша</GRAMMAR_ERROR>.
 * А <GRAMMAR_ERROR descr="grammar_vse_li_noun">все ли ошибка</GRAMMAR_ERROR> найдены?
 * Это случилось <GRAMMAR_ERROR descr="INVALID_DATE">31 ноября</GRAMMAR_ERROR> 2014 г.
 * За весь вечер она <GRAMMAR_ERROR descr="ne_proronila_ni">не проронила и слово</GRAMMAR_ERROR>.
 * Собрание состоится в <GRAMMAR_ERROR descr="RU_COMPOUNDS">конференц зале</GRAMMAR_ERROR>.
 * <GRAMMAR_ERROR descr="WORD_REPEAT_RULE">Он он</GRAMMAR_ERROR> ошибка.
 */
class ForMultiLanguageSupport {
    // Er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um ganz <GRAMMAR_ERROR descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</GRAMMAR_ERROR>.
    // Das ist <GRAMMAR_ERROR descr="FUEHR_FUER">führ</GRAMMAR_ERROR> Dich!
    // Das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <STYLE_SUGGESTION descr="MANNSTUNDE">Mannstunden</STYLE_SUGGESTION>.
}

/**
 * Returns `an true` if expression is part of when condition expression that looks like
 * ```
 * when {
 * a && b -> ...
 * a && !b -> ...
 * }
 * ```
 * * This is <GRAMMAR_ERROR descr="EN_A_VS_AN">a</GRAMMAR_ERROR> error.
 * ```
 * An non-checked code fragment
 * ```
 */