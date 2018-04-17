import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class Test {
  int test(File jarFile, String subPath) throws IOException {
      final JarFile jFile = new JarFile(jarFile);
      final ZipEntry entry = jFile.getEntry(subPath);
      if (entry == null) {
        throw new RuntimeException();
      }
      try (InputStream stream = jFile.getInputStream(entry)) {
          return stream.read();
      } finally {
          jFile.close();
      }
  }
}