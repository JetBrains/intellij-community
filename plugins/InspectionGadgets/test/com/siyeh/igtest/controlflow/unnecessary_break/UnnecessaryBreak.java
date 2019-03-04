public class UnnecessaryBreak {
  void example(boolean a, boolean b) {
    foo: {
      if (a) {
        if (b) {
          // ...
        } else {
          // ...
          <warning descr="'break' statement is unnecessary">break</warning> foo;
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
                <warning descr="'break' statement is unnecessary">break</warning>;
            }
            default -> {
                <warning descr="'break' statement is unnecessary">break</warning>;
            }
        }
    }
}