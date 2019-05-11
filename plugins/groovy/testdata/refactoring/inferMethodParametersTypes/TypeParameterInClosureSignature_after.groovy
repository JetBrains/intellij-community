import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T0 extends X & groovy.lang.GroovyObject> void foo(@ClosureParams(value = FromString, options = ["T0"]) Closure<?> c) {
  c(new A())
  c(new B())
}

interface X{void foo()}
class A implements X{void foo(){}}
class B implements X{void foo(){}}

foo({it.foo()})