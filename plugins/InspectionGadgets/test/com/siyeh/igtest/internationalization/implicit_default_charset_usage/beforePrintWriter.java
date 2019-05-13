// "Specify UTF-8 charset" "false"
import java.io.*;

class X {
  void test(OutputStream os) {
    // no PrintWriter(os, charset), at least in Java 9
    Writer writer = new PrintWr<caret>iter(os);
  }
}