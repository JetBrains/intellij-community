def a = 4
print (++a)
print (a<warning descr="Unused '++'">++</warning>)

def b = 3
b<warning descr="Unused '++'">++</warning>
       <warning descr="Assignment is not used">b</warning> = 3