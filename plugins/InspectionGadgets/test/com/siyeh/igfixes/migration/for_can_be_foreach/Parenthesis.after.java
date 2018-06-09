import java.io.File;
import java.util.List;

class Test {
  void foo(List<String> files) {
      for (String file: files) {
          new File(file);
      }
  }
}

