import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

class A<T> {

  List<T> x

    Object fo<caret>o(List<T> a, @ClosureParams(value = SimpleType, options = ['?']) Closure<Void> b) {
    x = a
  }
}
new A<Integer>().foo([1]) {}