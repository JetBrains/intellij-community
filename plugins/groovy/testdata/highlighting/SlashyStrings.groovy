print (/abc/)
print (<error descr="Multi-line slashy strings are not supported in Groovy 1.6.9-sources">/ab
c/</error>)

print (/abc${false}def/)

print (<error descr="Multi-line slashy strings are not supported in Groovy 1.6.9-sources">/abc${false}d
ef/</error>)