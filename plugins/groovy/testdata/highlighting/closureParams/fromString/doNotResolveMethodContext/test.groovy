import com.foo.baz.A
import com.foo.baz.MyClass
import groovy.transform.CompileStatic

@CompileStatic
class Usage {
  def foo() { A.foo { <error descr="Expected 'MyClass', found 'com.foo.baz.MyClass'">MyClass</error> a -> } }
}
