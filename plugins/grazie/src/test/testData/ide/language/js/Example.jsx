function formatName(user) {
    return user.firstName + ' ' + user.lastName;
}

const user = {
    firstName: '<TYPO descr="Typo: In word 'eror'">eror</TYPO>',
    lastName: 'it <warning descr="IT_VBZ">are</warning> bad'
};

const element = (
    <h1>
        { (a, b) -> a + "it <warning descr="IT_VBZ">are</warning> <TYPO descr="Typo: In word 'eror'">eror</TYPO>" + user  }
        it <warning descr="IT_VBZ">are</warning> bad,
        <p>
            it is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human
        </p>

        <warning descr="PRP_VBG">It working</warning> for <warning descr="MUCH_COUNTABLE">much</warning> warnings, {formatName(user)}!
    </h1>
);
