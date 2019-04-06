interface Function<D, F> {
  F fun(D d)
}

def foo(Function<String, String> function) {
  //   print function.fun('abc')
}


foo<warning>(it-> { "1" } as Function)</warning>
