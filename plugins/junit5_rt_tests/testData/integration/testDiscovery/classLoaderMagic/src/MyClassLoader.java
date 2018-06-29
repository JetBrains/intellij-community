import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public class MyClassLoader extends ClassLoader {

  public MyClassLoader(ClassLoader extClassLoader) {
    super(extClassLoader);
  }

  public static void main(String[] args) throws Exception {
    doMagic(ClassLoader.getSystemClassLoader().getParent());
  }

  public static void doMagic(ClassLoader extClassLoader) throws Exception {
    byte[] bytes = getMagicClassBytes();
    MyClassLoader classLoader = new MyClassLoader(extClassLoader);
    classLoader.defineClass("Magic", bytes, 0, bytes.length);
    Class<?> magic = classLoader.loadClass("Magic");
    Method abracadabra = magic.getDeclaredMethod("abracadabra");
    abracadabra.setAccessible(true);
    abracadabra.invoke(null);
  }

  private static byte[] getMagicClassBytes() throws IOException {
    InputStream is = getSystemClassLoader().getResourceAsStream("Magic.class");
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      int nRead;
      byte[] data = new byte[16384];

      while ((nRead = is.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }

      buffer.flush();
      return buffer.toByteArray();
    }
    finally {
      is.close();
    }
  }
}

class Magic {
  public static void abracadabra() {
    System.out.println("abracadabra");
  }
}