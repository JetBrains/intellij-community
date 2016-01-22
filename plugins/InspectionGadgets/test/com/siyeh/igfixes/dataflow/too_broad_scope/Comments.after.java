import java.io.*;

public class Comments {
  void a() {
      try (PrintStream out = System.out) {
          int s = out.hashCode(); // a // b
          out.println(s);
    }
  }
}