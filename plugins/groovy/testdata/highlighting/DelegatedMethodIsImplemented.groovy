interface X<T> {
    def foo(X<?> x)
}

class A {
    @Delegate public X list = []
}

<error descr="Method 'foo' is not implemented">class Y implements X</error>{
  def foo(X<String> x){}
}