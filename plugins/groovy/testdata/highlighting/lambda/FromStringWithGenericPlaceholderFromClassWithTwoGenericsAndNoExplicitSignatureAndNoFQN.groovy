import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

class Foo<T,U> {
  public void foo(@ClosureParams(value=FromString,options="List<U>") Closure cl) { cl.call(['hey','ya']) }
}

@CompileStatic
def test() {
  def foo = new Foo<Integer,String>()

  foo.foo (it) -> { it.each (a) -> { println a.toUpperCase() } }
}