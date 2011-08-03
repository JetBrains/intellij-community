interface X<T> {
    def foo(X<?> x)
}

class A {
    @Delegate public X list = []
}

class <error descr="Method 'foo' is not implemented">Y</error> implements X{
  def foo(X<?> x){}
}