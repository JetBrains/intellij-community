class Cat {
  static foo(Class c, int x) {}
}

class X {}

use(Cat, () -> {
  X.foo(1)
})

