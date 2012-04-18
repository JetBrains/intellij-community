def foo(Closure<String> c) {
  return c().substring(1)
}

foo {
  print 2
<warning descr="Not all execution paths return a value">}</warning>

foo {
  's'
}

foo {
  if (a) 's'
<warning descr="Not all execution paths return a value">}</warning>

foo {
  if (a) 'a' else 'b'
}
