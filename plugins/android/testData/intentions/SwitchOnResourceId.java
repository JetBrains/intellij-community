package p1.p2;

public class Test1 {
  public void f(int n) {
    switch (n) {
      case <error><warning descr="Resource IDs cannot be used in a switch statement in Android library modules">R.dr<caret>awable.icon</warning></error>:
        System.out.println("Icon");
        break;
    }
  }
}