var oneTypo = "It is <warning descr="EN_A_VS_AN">an</warning> friend of human";
var oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human";
var fewTypos = "It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings";
var ignoreTemplate = "It is ${fewTypos} friend";
var notIgnoreOtherMistakes = "It is friend. But I have a ${1} here";

var oneTypo = 'It is <warning descr="EN_A_VS_AN">an</warning> friend of human';
var oneSpellcheckTypo = 'It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human';
var fewTypos = 'It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings';
var ignoreTemplate = 'It is ${fewTypos} friend';
var notIgnoreOtherMistakes = 'It is friend. But I have a ${1} here';

console.log("It is <warning descr="EN_A_VS_AN">an</warning> friend of human");
console.log("It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human");
console.log("It <warning descr="IT_VBZ">are</warning> working for <warning descr="MUCH_COUNTABLE">much</warning> warnings");
console.log("It is ${fewTypos} friend");
console.log("It is friend. But I have a ${1} here");
