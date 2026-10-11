import junit.framework.TestCase

class Doo extends TestCase {
  void testBar() {
    void <error descr="Method declaration not allowed here"><warning descr="Method foo is unused">foo</warning></error>() {

    }
  }

  void <warning descr="Method foo is unused">foo</warning>() {}
}

