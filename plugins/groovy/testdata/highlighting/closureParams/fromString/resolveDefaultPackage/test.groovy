import com.foo.baz.A
import groovy.transform.CompileStatic

@CompileStatic
class Usage {
    def foo() { A.bar { MyClass a -> } }
}
