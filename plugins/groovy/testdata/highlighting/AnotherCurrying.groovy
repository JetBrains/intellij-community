def cl = {String s, int x -> print "fp"}

cl = cl.ncurry(0, "a")

cl<warning descr="'call' cannot be applied to '(java.lang.String)'">("a")</warning>
cl(2)