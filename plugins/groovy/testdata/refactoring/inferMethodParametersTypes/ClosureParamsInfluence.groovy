import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <caret> foo(list, cl) {
  bar(list, cl)
}

def <T> void bar(Collection<T> list, @ClosureParams(FirstParam.FirstGenericType) Closure<Boolean> cl) {
}

foo([1]) { it % 100500 == 0}
foo(['q']) { it.charAt(100500) == '\0'.toCharArray()[0]}