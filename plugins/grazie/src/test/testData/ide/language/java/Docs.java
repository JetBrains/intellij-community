/**
 * A group of *members*.
 * <p>
 * This class has no useful logic; it's just a documentation example.
 *
 * @param T the type of member in this group.
 */
class ExampleClassWithNoTypos<T> {

    private String name;

    /**
     * Creates an empty group.
     *
     * @param name The name of the group.
     */
    public ExampleClassWithNoTypos(String name) {
        this.name = name;
    }

    /**
     * Adds a [member] to this group.
     *
     * @param member member to add
     * @return the new size of the group.
     */
    Integer goodFunction(T member) {
        return 1; // no error comment
    }
}

/**
 * It is friend there
 *
 * This guy <warning descr="MASS_AGREEMENT">have</warning> no useful logic; it's just a documentation example.
 *
 * @param T the <warning descr="KIND_OF_A">type of a</warning> <TYPO descr="Typo: In word 'membr'">membr</TYPO> in this group.
 */
class ExampleClassWithTypos<T> {

    private String name;

    /**
     * Creates an empty group.
     *
     * @param name the <warning descr="COMMA_WHICH">name which</warning> group
     */
    public ExampleClassWithTypos(String name) {
        this.name = name;
    }

    /**
     * It <warning descr="IT_VBZ">add</warning> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>.
     *
     * @param member member to add
     * @return the new size of <warning descr="DT_DT">a the</warning> group.
     */
    Integer badFunction(T member) {
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
