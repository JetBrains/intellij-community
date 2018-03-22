import java.io.*;

class Dependents {
  void m() throws IOException {
      String filename = "Bar";
      filename = "filename";
      try (InputStream in = new FileInputStream(filename)) {
      }
  }
}