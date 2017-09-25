import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
    String localVar = "";
    new <warning descr="Anonymous class 'B' may be a named 'static' inner class">B</warning> () {
      void f() {
        System.out.println(localVar);
      }
    };
  }

  class A {}
  static class B {}

  void m() {
    class C {
    }
    new B() {
      void bla() {
        C b; // reference to local class
      }
    };
    new <warning descr="Anonymous class 'B' may be a named 'static' inner class">B</warning>() {
      void bla() {
        AnonymousInnerClassMayBeStatic.n();
      }
    };
  }

  static void n() {}

  class D {
    {new E().m();}
    class E {
      private void m() {}
    }
  }

  class CC {}
  static class BB<T>  {
    void m() {
      new BB<T>() {
        class Z {}
      };
    }
  }

  String t = "";

  void m(int p) {
    String s = null;
    new <warning descr="Anonymous class 'Object' may be a named 'static' inner class">Object</warning>() {
      private int a = 1;
      void f() {
        System.out.println(a);
        System.out.println(p);
        System.out.println(s);
        this.g();
      }

      private void g() {}
    };
  }

  void sort(List<String> list) {
    Collections.sort(list, new <warning descr="Anonymous class 'Comparator<String>' may be a named 'static' inner class">Comparator<String></warning>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.toString().compareToIgnoreCase(o2.toString());
      }
    });
  }
}
class One {
  class Two {
    void foo() {
      new Object() {};
    }
  }
}