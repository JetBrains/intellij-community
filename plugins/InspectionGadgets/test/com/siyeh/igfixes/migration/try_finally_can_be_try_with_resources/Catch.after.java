import java.io.*;

class Catch {
  void m() throws IOException {
      try (InputStream in = new FileInputStream("filename")) {
      } catch (Exception e) {
      }
  }
}