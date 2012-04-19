package p1.p2;

public class Test1 {
  public void f(int n) {
    switch (n) {
      case <error descr="Resource IDs cannot be used in a switch statement in Android library modules"><error descr="Constant expression required">R.dr<caret>awable.icon</error></error>:
        System.out.println("Icon");
        break;
    }
  }
}