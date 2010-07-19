class A<E> {}

interface Base {}
interface I1 extends Base {}
interface I2 extends Base {}

def <E> A<E> or(A<? extends E>... e) {return null}

def a1 = new A<I1>()
def a2 = new A<I2>()
o<ref>r(a1, a2)

