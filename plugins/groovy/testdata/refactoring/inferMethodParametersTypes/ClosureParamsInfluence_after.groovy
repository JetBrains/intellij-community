import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.FromString

def <V0> Object foo(List<? extends V0> list, @ClosureParams(value = FromString, options = ["V0"]) Closure<? extends Boolean> cl) {
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