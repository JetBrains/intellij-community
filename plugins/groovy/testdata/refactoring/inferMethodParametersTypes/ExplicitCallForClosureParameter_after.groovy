import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

static void foo(ArrayList<? extends Integer> self, @ClosureParams(value = SimpleType, options = ['java.lang.Integer', '? super java.lang.Integer']) Closure<?> closure) {
  final Object[] args = new Object[2]
  closure.call(self[0], 0)
}


foo([1, 2, 3], {list, ind -> println(list)})