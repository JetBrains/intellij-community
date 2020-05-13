# TODO add f-Strings support
oneTypo = "It is <warning descr="ARTICLE_MISSING">friend</warning> of human"
oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human"
fewTypos = "It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings"
ignoreTemplate = "It is {} friend" % fewTypos
notIgnoreOtherMistakes = "It is <warning descr="ARTICLE_MISSING">friend</warning>. <warning descr="And">But</warning> I have a {1} here"

oneTypo = 'It is <warning descr="ARTICLE_MISSING">friend</warning> of human'
oneSpellcheckTypo = 'It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human'
fewTypos = 'It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings'
ignoreTemplate = 'It is {} friend' % fewTypos
notIgnoreOtherMistakes = 'It is <warning descr="ARTICLE_MISSING">friend</warning>. <warning descr="And">But</warning> I have a {1} here'

print('It is <warning descr="ARTICLE_MISSING">friend</warning> of human')
print('It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human')
print('It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings')
print('It is {} friend' % fewTypos)
print('It is <warning descr="ARTICLE_MISSING">friend</warning>. <warning descr="And">But</warning> I have a {1} here')
