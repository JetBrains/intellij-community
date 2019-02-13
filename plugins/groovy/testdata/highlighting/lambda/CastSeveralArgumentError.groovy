import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

void forEachTyped(Map<String, Integer> self, @ClosureParams(value=SimpleType.class, options=["java.lang.String", "java.lang.Integer"]) Closure closure) {
  self.each (k, v) -> {
     closure(k, v)
  };
}

@CompileStatic
def m() {
  Map<String,Integer> m = [:]
  forEachTyped(m, (String key, <error>String</error> value) -> {
    println( key + ' ' + value)
    }
  )
}
