class X{
  def a;

  static private final def f<caret>oo = {def x, def y ->
    print x + a;
    print y;
  }
}