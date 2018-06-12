import java.io.*;

class Dependents {
  void m() throws IOException {
    InputStream in = null;
    String filename = "Bar";
    try<caret> {
      filename = "filename";
      in = new FileInputStream(filename);
    }finally{
      in.close();
    }
  }
}