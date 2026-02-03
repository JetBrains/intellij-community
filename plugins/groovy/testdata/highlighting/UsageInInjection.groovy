def x = new Date()
def y = new Date()
def <warning descr="Assignment is not used">z</warning> = new Date()
assert false : "should have thrown exception, but returned $x"
assert false : "should have thrown exception, but returned ${y}"