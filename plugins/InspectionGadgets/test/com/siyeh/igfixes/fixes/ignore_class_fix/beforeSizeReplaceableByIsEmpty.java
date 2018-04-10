// "Ignore '.size()' calls on type 'java.util.ArrayList'" "true"
import java.util.ArrayList;

class X {
  boolean x(ArrayList<String> list) {
    return list.<caret>size() == 0;
  }
}