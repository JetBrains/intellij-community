import com.foo.baz.A
import groovy.transform.CompileStatic

@CompileStatic
class Usage {
  def foo() { new A<Integer>().bar (<error descr="Expected 'MyClass<java.lang.Integer>', found 'MyClass<java.lang.String>'">MyClass<String></error> a) -> {  } }
}
