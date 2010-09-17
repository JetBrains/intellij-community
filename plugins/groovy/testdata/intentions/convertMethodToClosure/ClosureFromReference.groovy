class X{
  def a;

  static private final def foo = {def x, def y ->
    print x + a;
    print y;
  }
}

X.f<caret>oo(2, 3)