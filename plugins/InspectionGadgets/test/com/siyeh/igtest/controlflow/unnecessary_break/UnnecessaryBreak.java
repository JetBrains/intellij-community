import java.util.Random;

public class UnnecessaryBreak {
  void example(boolean a, boolean b) {
    foo: {
      if (a) {
        if (b) {
          // ...
        } else {
          // ...
          <warning descr="'break foo;' statement is unnecessary">break foo;</warning>
        }

        // Code was removed here, making the break unnecessary.
      } else {
        // ...
      }
    }
  }

  void necessary(boolean a) {
    brake: {
      if (a) {
        break brake;
      }
      System.out.println();
    }
  }
}
class Switch {
    enum E { A, B, C}
    void x(E e) {
        switch (e) {
            case A, B, C -> {
                <warning descr="'break;' statement is unnecessary">break;</warning>
            }
            default -> {
                <warning descr="'break;' statement is unnecessary">break;</warning>
            }
        }
    }
}
class JetbrainsBugReport {
  public static void main(String[] args) {
    Random r = new Random();
    boolean b = r.nextBoolean();
    label1: if(b) {
      int k = 0;
      <warning descr="'break label1;' statement is unnecessary">break label1;</warning>
    }
    else {
      System.out.println("Example");
    }
  }
}