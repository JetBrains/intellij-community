class Main {
    public static void main(String[] args) {
        String oneTypo = "It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human";
        String oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human";
        String fewTypos = "It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings";
        String ignoreTemplate = "It is ${1} friend";
        String notIgnoreOtherMistakes = "It is friend. But I have a ${1} here";
        String typoInTextBlock = """
                                 Lorem ipsum dolor sit amet, \
                                 <TYPO descr="Typo: In word 'onsectetur'">onsectetur</TYPO>...
                                 """;

        System.out.println("It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human");
        System.out.println("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human");
        System.out.println("It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings");
        System.out.println("It is ${1} friend");
        System.out.println("It is friend. But I have a ${1} here");
        System.out.println("The path is ../data/test.avi");
    }

    String messagePart = "; fragment of one sentence. This is another sentence,";

    String gitCherryPickPattern = "(cherry picked from "; // hard-coding the string git outputs
    String nonGitCherryPick = "I'd like to <GRAMMAR_ERROR descr="EN_COMPOUNDS_CHERRY_PICK">cherry pick</GRAMMAR_ERROR> this";
}
