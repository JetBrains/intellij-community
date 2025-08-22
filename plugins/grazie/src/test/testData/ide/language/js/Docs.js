/**
 * Module description <TYPO descr="Typo: In word 'eror'">eror</TYPO>
 * @module ExampleClassWithNoTypos
 */

/**
 * A group of *members*.
 *
 * This class has no useful logic; it's just a documentation example.
 */
class ExampleClassWithNoTypos {
    /**
     * Creates an empty group
     * @param  {String} name the name of the group. And another sentence.
     */
    constructor(name) {
        /** @private */
        this.name = name;
    }

    /**
     * Adds a [member] to this group.
     * @param {String} member member to add
     * @return {Number} the new size of the group.
     */
    goodFunction(member) {
        return 1; // no error comment
    }
}

/**
 * It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
 */
class ExampleClassWithTypos {
    /**
     * Creates an empty group
     * @param  {String} name the <GRAMMAR_ERROR descr="COMMA_WHICH">name which</GRAMMAR_ERROR> group
     */
    constructor(name) {
        /** @private */
        this.name = name;
    }

    /**
     * It <GRAMMAR_ERROR descr="IT_VBZ">add</GRAMMAR_ERROR> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>.
     * @param {String} member member to add
     * @return {Number} the new size <GRAMMAR_ERROR descr="DT_DT">a the</GRAMMAR_ERROR> group.
     */
    badFunction(member) {
        return 1; // It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'eror'">eror</TYPO> in the comment
    }
}

/**
 * В коробке лежало <GRAMMAR_ERROR descr="Sklonenije_NUM_NN">пять карандаша</GRAMMAR_ERROR>.
 * А <GRAMMAR_ERROR descr="grammar_vse_li_noun">все ли ошибка</GRAMMAR_ERROR> найдены?
 * Это случилось <GRAMMAR_ERROR descr="INVALID_DATE">31 ноября</GRAMMAR_ERROR> 2014 г.
 * За весь вечер она <GRAMMAR_ERROR descr="ne_proronila_ni">не проронила и слово</GRAMMAR_ERROR>.
 * Собрание состоится в <GRAMMAR_ERROR descr="RU_COMPOUNDS">конференц зале</GRAMMAR_ERROR>.
 * <GRAMMAR_ERROR descr="WORD_REPEAT_RULE">Он он</GRAMMAR_ERROR> ошибка.
 */
class ForMultiLanguageSupport {
    // er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um <GRAMMAR_ERROR descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</GRAMMAR_ERROR>.
    // das ist <GRAMMAR_ERROR descr="FUEHR_FUER">führ</GRAMMAR_ERROR> Dich!
    // das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <STYLE_SUGGESTION descr="MANNSTUNDE">Mannstunden</STYLE_SUGGESTION>.
}

module.exports = ExampleClassWithNoTypos;
