class Main {
    public static void main(String[] args) {
        String oneTypo = "It is <warning descr="EN_A_VS_AN">an</warning> friend of human";
        String oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human";
        String fewTypos = "It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings";
        String ignoreTemplate = "It is ${1} friend";
        String notIgnoreOtherMistakes = "It is friend. But I have a ${1} here";
        String typoInTextBlock = """
                                 Lorem ipsum dolor sit amet, \
                                 <TYPO descr="Typo: In word 'consectetur'">consectetur</TYPO>...
                                 """;

        System.out.println("It is <warning descr="EN_A_VS_AN">an</warning> friend of human");
        System.out.println("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human");
        System.out.println("It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings");
        System.out.println("It is ${1} friend");
        System.out.println("It is friend. But I have a ${1} here");
        System.out.println("The path is ../data/test.avi");
    }

    String gitCherryPickPattern = "(cherry picked from "; // hard-coding the string git outputs
    String nonGitCherryPick = "I'd like to <warning descr="EN_COMPOUNDS">cherry pick</warning> this";
}
