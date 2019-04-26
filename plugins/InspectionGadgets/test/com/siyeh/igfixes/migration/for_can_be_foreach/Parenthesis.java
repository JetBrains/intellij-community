import java.io.File;
import java.util.List;

class Test {
  void foo(List<String> files) {
    fo<caret>r/*1*/ (int i = (0)/*2*/; i < ((files)/*3*/.size()); ++/*4*/(i)) {
      new File(((files/*5*/).get((i))));/*6*/
    }
  }
}

