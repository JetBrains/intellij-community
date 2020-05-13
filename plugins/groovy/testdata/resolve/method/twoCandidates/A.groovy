interface I{}
interface J{}
class A implements I,J {}

def f(I i) {}
def f(J j) {}

<caret>f(new A())