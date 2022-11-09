const test1 = "test1" + "abc".replace("a", "aaa") + "\n"
// const test1 = "$__Variable0$" + "$__Variable1$".replace("$__Variable2$", "$__Variable3$") + "$__Variable4$"
const test2 = test1 + '' + `blabla ${test1} blablabla` + "ab\n"
// const test2 = test1 + '$__Variable0$' + `blabla ${test1} blablabla` + "$__Variable1$"
const test3 = "string+\n\t\r" + 'string+\n\t\r' + `string+\n\t\r`
// const test3 = "$__Variable0$" + '$__Variable1$' + `string+\n\t\r`
const test4 = "double" + 'single' + `backtick` + 1234 + test1
// const test4 = "$__Variable0$" + '$__Variable1$' + `backtick` + 1234 + test1
