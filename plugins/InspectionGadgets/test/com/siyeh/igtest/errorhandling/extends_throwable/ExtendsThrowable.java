public class <warning descr="class 'ExtendsThrowable' directly extends 'java.lang.Throwable'">ExtendsThrowable</warning> extends Throwable {

  void f() {
    new <warning descr="Anonymous class directly extends 'java.lang.Throwable'">Throwable</warning>() {
      void b() {}
    };
  }

<T extends Throwable> void foo() throws T {}
}
class E1 extends Exception {}