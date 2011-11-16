print (/abc/)
print (<error descr="Multi-line slashy strings are not allowed in Groovy 1.6">/ab
c/</error>)

print (<error descr="Slashy strings with injections are not allowed in Groovy 1.6">/abc${false}def/</error>)

print (<error descr="Multi-line slashy strings are not allowed in Groovy 1.6">/abc${false}d
ef/</error>)