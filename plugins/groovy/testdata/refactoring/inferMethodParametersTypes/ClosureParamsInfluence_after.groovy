import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.FromString

def <T> Object foo(ArrayList<? extends T> list, @ClosureParams(value = FromString, options = ["T"]) Closure<Boolean> cl) {
  bar(list, cl)
}

def <T> void bar(Collection<T> list, @ClosureParams(FirstParam.FirstGenericType) Closure<Boolean> cl) {
}

foo([1]) { it % 100500 == 0}
foo(['q']) { it.charAt(100500) == '\0'.toCharArray()[0]}