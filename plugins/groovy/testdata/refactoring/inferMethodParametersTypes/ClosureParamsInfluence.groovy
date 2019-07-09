import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <caret> foo(list, cl) {
  bar(list, cl)
}

def <T> void bar(Collection<T> list, @ClosureParams(FirstParam.FirstGenericType) Closure<Boolean> cl) {
}

def m(A a, B b) {
  foo([a]) { it % 100500 == 0}
  foo([b]) { it.charAt(100500) == '\0'.toCharArray()[0]}
}

class A{}
class B{}