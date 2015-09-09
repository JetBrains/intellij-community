import java.io.File;
import java.util.List;

class Test {
  void foo(List<String> files) {
    fo<caret>r (int i = 0; i < (files).size(); ++i) {
      new File(files.get(i));
    }
  }
}

