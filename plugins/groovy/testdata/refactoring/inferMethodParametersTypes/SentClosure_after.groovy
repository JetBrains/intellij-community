import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static void foo(@ClosureParams(value = FromString, options = ["Object"]) Closure<?> script) {
  bar(script, 1)
}

void baz() {
  foo {}
}

void bar(Closure script, a) {
}
