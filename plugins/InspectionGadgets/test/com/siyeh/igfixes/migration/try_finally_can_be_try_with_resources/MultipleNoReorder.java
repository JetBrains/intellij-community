import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class Test {
  int test(File jarFile, String subPath) throws IOException {
    InputStream stream = null;
    final JarFile jFile = new JarFile(jarFile);
    try<caret> {
      final ZipEntry entry = jFile.getEntry(subPath);
      if (entry == null) {
        throw new RuntimeException();
      }
      stream = jFile.getInputStream(entry);
      return stream.read();
    } finally {
      if (stream != null) {
        stream.close();
      }
      jFile.close();
    }
  }
}