print (/abc/)
print (<error descr="Multi-line slashy strings are not allowed in Groovy <no version>">/ab
c/</error>)

print (<error descr="Slashy strings with injections are not allowed in Groovy <no version>">/abc${false}def/</error>)

print (<error descr="Slashy strings with injections are not allowed in Groovy <no version>">/abc${false}d
ef/</error>)