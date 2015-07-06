import java.io.*;

class Dependents {
  void m() throws IOException {
    InputStream in = null;
    try<caret> {
      String filename = "filename";
      in = new FileInputStream(filename);
    }finally{
      in.close();
    }
  }
}