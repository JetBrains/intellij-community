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
 * It is <warning descr="EN_A_VS_AN">an</warning> friend of human
 */
class ExampleClassWithTypos {
    /**
     * Creates an empty group
     * @param  {String} name the <warning descr="COMMA_WHICH">name which</warning> group
     */
    constructor(name) {
        /** @private */
        this.name = name;
    }

    /**
     * It <warning descr="IT_VBZ">add</warning> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>.
     * @param {String} member member to add
     * @return {Number} the new size <warning descr="DT_DT">a the</warning> group.
     */
    badFunction(member) {
        return 1; // It <warning descr="IT_VBZ">are</warning> <TYPO descr="Typo: In word 'eror'">eror</TYPO> in the comment
    }
}

/**
 * В коробке лежало <warning descr="Sklonenije_NUM_NN">пять карандаша</warning>.
 * А <warning descr="grammar_vse_li_noun">все ли ошибка</warning> найдены?
 * Это случилось <warning descr="INVALID_DATE">31 ноября</warning> 2014 г.
 * За весь вечер она <warning descr="ne_proronila_ni">не проронила и слово</warning>.
 * Собрание состоится в <warning descr="RU_COMPOUNDS">конференц зале</warning>.
 * <warning descr="WORD_REPEAT_RULE">Он он</warning> ошибка.
 */
class ForMultiLanguageSupport {
    // er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um ganz <warning descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</warning>.
    // das ist <warning descr="FUEHR_FUER">führ</warning> Dich!
    // das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <warning descr="MANNSTUNDE">Mannstunden</warning>.
}

module.exports = ExampleClassWithNoTypos;
