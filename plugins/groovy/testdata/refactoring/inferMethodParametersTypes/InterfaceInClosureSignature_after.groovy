import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

void foo(@ClosureParams(value = SimpleType, options = ['? super X']) Closure<?> c) {
  c(new A())
  c(new B())
}

interface X{void foo()}
class A implements X{void foo(){}}
class B implements X{void foo(){}}

foo({it.foo()})