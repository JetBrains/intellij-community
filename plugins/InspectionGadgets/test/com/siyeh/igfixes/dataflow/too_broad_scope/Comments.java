import java.io.*;

public class Comments {
  void a() {
    int <caret>s; // a
    try (PrintStream out = System.out) {
      s = out.hashCode(); // b
      out.println(s);
    }
  }
}