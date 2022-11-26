test1 = "test1" + "".join(random.choice(string.ascii_letters) for _ in range(10)) + "\n"
# test1 = "$__Variable0$" + "$__Variable1$".join(random.choice(string.ascii_letters) for _ in range(10)) + "$__Variable2$"
test2 = test1 + "ab" + "".join(random.choice(string.ascii_letters), ) + "ab\n" + "test2" + "".join()
# test2 = test1 + "$__Variable0$" + "$__Variable1$".join(random.choice(string.ascii_letters), ) + "$__Variable2$" + "$__Variable3$" + "$__Variable4$".join()
test3 = f"f-string+\n\t\r" + r"r-string+\n\t\r"
# test3 = f"$__Variable0$" + r"$__Variable1$"
test4 = "123" + """doc-string""" + 1234 + f"f-string" + test1
# test4 = "$__Variable0$" + """doc-string""" + 1234 + f"$__Variable2$" + test1
