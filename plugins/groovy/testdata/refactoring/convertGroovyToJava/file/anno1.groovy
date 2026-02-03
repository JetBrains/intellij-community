enum E {
  A, B
}
@interface Inter {
  I[] bar() default [@I(2), @I(a = 5)]
  String[] strings() default ["a"];
  E foo() default E.A
}

@interface I {
  int a() default 4
}