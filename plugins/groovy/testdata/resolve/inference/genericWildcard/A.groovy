class X<E> {}

interface Base {}
interface I1 extends Base {}
interface I2 extends Base {}

def <E> X<E> or(X<? extends E>... e) {return null}

def x1 = new X<I1>()
def x2 = new X<I2>()
o<ref>r(x1, x2)
