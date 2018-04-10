import java.io.*;

class Catch {
  void m() throws IOException {
    InputStream in = null;
    try<caret> {
      in = new FileInputStream("filename");
    }catch (Exception e) {
    }finally{
      in.close();
    }
  }
}