void foo(c) {
  c(new A())
  c(new B())
}

interface X{void foo()}
class A implements X{void foo(){}}
class B implements X{void foo(){}}

foo({it.foo()})