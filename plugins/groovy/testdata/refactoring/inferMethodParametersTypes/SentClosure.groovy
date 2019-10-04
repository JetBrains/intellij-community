static void foo(script) {
  bar(script, 1)
}

void baz() {
  foo {}
}

void bar(Closure script, a) {
}
