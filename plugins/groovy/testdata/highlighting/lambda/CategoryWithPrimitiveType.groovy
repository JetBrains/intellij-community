class Cat {
  static foo(Integer x) {}
}

use(Cat,()-> {
  1.with {
    foo()
  }

  (1 as int).foo()
});

class Ca {
  static foo(int x) {}
}

use(Ca, () -> {
  1.<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
  (1 as int).<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
})