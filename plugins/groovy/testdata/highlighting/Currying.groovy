def cl = {Map m, String s, int x, def y ->print "foo"}

cl = cl.rcurry(2, 4)
cl = cl.ncurry(1, "a")
cl(a:2)
cl<warning descr="'cl' cannot be applied to '()'">()</warning>
