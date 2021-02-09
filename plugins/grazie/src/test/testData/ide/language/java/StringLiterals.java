class Main {
    public static void main(String[] args) {
        String oneTypo = "It is <warning descr="BEEN_PART_AGREEMENT">friend</warning> of human";
        String oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human";
        String fewTypos = "It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings";
        String ignoreTemplate = "It is ${1} friend";
        String notIgnoreOtherMistakes = "It is friend. <warning descr="And">But</warning> I have a ${1} here";

        System.out.println("It is <warning descr="BEEN_PART_AGREEMENT">friend</warning> of human");
        System.out.println("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human");
        System.out.println("It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings");
        System.out.println("It is ${1} friend");
        System.out.println("It is friend. <warning descr="And">But</warning> I have a ${1} here");
    }
}
