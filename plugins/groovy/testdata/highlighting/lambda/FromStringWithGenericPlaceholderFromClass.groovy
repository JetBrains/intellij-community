import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

class Foo<T> {
  public void foo(@ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call(['hey', 'ya']) }
}

@CompileStatic
def test() {
  def foo = new Foo<String>()

  foo.foo(List<String> str) -> { str.each((it) -> { println it.toUpperCase() } ) }
}