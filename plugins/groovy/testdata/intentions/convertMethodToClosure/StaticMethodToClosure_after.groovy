class X{
  def a;

    static private final def fo<caret>o = { def x, def y ->
        print x + a;
        print y;
    }
}