<error descr="Single argument form of lambda is available only as right part of assignment expression or as argument inside method call">a</error> -> a
def l = b-> <error descr="Single argument form of lambda is available only as right part of assignment expression or as argument inside method call">c</error>-> d
def l2 = false ? <error descr="Single argument form of lambda is available only as right part of assignment expression or as argument inside method call">e</error>->e : <error descr="Single argument form of lambda is available only as right part of assignment expression or as argument inside method call">f</error>->f
if ((e->e) != null){}
m(g->g)
def l3 = h->h
l3 = j->j
