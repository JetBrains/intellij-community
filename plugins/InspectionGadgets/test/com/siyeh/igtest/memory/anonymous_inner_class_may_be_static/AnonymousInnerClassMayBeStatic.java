public class AnonymousInnerClassMayBeStatic {

  public void foo()
  {
    final Runnable runnable = new <warning descr="Anonymous class 'Runnable' may be a named 'static' inner class">Runnable</warning>(){
      public void run() {
      }
    };
    runnable.run();
    new A() {};
    new <warning descr="Anonymous class 'B' may be a named 'static' inner class">B</warning>() {};
    new <error descr="Cannot resolve symbol 'C'">C</error>() {};
  }

  class A {}
  static class B {}
}