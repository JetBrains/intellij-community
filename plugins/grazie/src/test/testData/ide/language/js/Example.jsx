function formatName(user) {
    return user.firstName + ' ' + user.lastName;
}

const user = {
    firstName: '<TYPO descr="Typo: In word 'eror'">eror</TYPO>',
    lastName: 'it <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> bad'
};

const element = (
    <h1>
        { (a, b) -> a + "it <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'eror'">eror</TYPO>. And here are some correct English words to make the language detector work." + user  }
        First sentence. it <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> bad,
        <p>
            it is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human
        </p>

        <GRAMMAR_ERROR descr="PRP_VBG">It working</GRAMMAR_ERROR> for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings, {formatName(user)}!
    </h1>
);
