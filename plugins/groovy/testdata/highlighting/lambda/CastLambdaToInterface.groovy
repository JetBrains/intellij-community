interface Function<D, F> {
  F fun(D d)
}

def foo(Function<String, String> function) {
  //   print function.fun('abc')
}


foo<warning descr="'foo' in 'CastLambdaToInterface' cannot be applied to '(Function<java.lang.Double,java.lang.Double>)'">((it)->{println  it.byteValue()} as Function<Double, Double>)</warning>
foo({println  it.substring(1)} as Function)
foo((it)->{println  it.substring(1)} as Function<String, String>)
foo((it)->{println it})
