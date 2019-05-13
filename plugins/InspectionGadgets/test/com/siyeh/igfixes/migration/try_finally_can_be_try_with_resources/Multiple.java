import test.MyBufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

class Multiple {

  void m() throws IOException {
    FileInputStream fileInputStream = null;
    MyBufferedInputStream bufferedInputStream = null;
    <caret>try {
      fileInputStream = new FileInputStream("");
      bufferedInputStream = new BufferedInputStream(fileInputStream);
    } finally {
      bufferedInputStream.close();
      fileInputStream.close();
    }
  }
}