import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

static void foo(@ClosureParams(value = SimpleType, options = ['?']) Closure<?> script) {
  bar(script, 1)
}

void baz() {
  foo {}
}

void bar(Closure script, a) {
}
