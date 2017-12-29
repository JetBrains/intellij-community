import java.io.*;

public class Comments {
  void a() {
    int /*some comment inside old declaration*/ <caret>s; // a
    try (PrintStream out = System.out) {
      s = out.hashCode(/*inside initializer*/); // b
      out.println(s);
    }
  }
}