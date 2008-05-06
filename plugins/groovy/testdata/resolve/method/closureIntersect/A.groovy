class Base {
    void foo() {}
}
class D extends Base {}

Closure cl
boolean rand = Math.random() < 0.5
if (rand)
    cl = {D  p -> p}
else
    cl = {Base  p -> p}

cl(new D()).<ref>foo()