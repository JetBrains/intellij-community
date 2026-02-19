var oneTypo = "It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human";
var oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human";
var fewTypos = "It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings";
var ignoreTemplate = "It is ${fewTypos} friend";
var notIgnoreOtherMistakes = "It is friend. But I have a ${1} here";

var oneTypo = 'It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human';
var oneSpellcheckTypo = 'It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human';
var fewTypos = 'It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings';
var ignoreTemplate = 'It is ${fewTypos} friend';
var notIgnoreOtherMistakes = 'It is friend. But I have a ${1} here';

console.log("It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human");
console.log("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human");
console.log("It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings");
console.log("It is ${fewTypos} friend");
console.log("It is friend. But I have a ${1} here");
