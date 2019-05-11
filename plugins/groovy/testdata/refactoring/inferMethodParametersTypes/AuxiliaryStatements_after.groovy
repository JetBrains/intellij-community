import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

public static void foo(List<Integer> self, @ClosureParams(value = FromString, options = ["Integer,Integer"]) Closure<?> closure) {
  final Object[] args = new Object[2];
  closure(self[0], 0)
}


foo([1, 2, 3], {list, ind -> println(list)})