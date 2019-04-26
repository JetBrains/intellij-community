import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

class Foo {
  void bar() {
    println 'Haha!'
  }
}

class Tor<D,U> {
  public void foo(@ClosureParams(value=FromString,options=["D,List<U>", "D"]) Closure cl) {
    if (cl.maximumNumberOfParameters==2) {
      cl.call(3, [new Foo(), new Foo()])
    } else {
      cl.call(3)
    }
  }
}

@CompileStatic
def test() {
  def tor = new Tor<Integer,Foo>()

  tor.foo (r, e) -> { r.times (a) -> { e.each (it) -> { it.bar() } } }
  tor.foo (it) -> { it.times (x) -> { println 'polymorphic' } }
}