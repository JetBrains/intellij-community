val item = 1
val test1 = "test1" + item.toString() + "" + 5 + "\n"
// val test1 = "$__Variable0$" + item.toString() + "$__Variable1$" + 5 + "$__Variable2$"
val test2 = "test2" + "abracadabra" + "string" + "\$__Variable10$" + "\n"
// val test2 = "$__Variable0$" + "$__Variable1$" + "$__Variable2$" + "$__Variable3$" + "$__Variable4$"
val test3 = "all kinds of spaces\n\t\r" + item + "  all  kinds  of  spaces2\n g\t g\r "
// val test3 = "$__Variable0$" + item + "$__Variable1$"
