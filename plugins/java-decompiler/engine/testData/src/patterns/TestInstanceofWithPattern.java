package patterns;

import java.util.Collection;
import java.util.List;

public class TestInstanceofWithPattern {

  void typePattern1(Object str) {
    if (str instanceof String s) {
      System.out.println(s);
    } else {
      System.out.println("no");
    }
  }

  void typePattern2(Object str) {
    if (!(str instanceof String)) {
      System.out.println("no");
      return;
    }
    String s = (String) str;
    if (s.length() > 3) {
      System.out.println(s);
    } else if (s.startsWith("a")) {
      System.out.println(s + "");
    }
  }

  void typePatternInBinaryExpr(Object str) {
    if (str instanceof String s && (s.length() > 1 || s.startsWith("a"))) {
      System.out.println(s);
    } else {
      System.out.println("no");
    }
  }

  String returnInstanceof(Object obj) {
    if (obj instanceof String s && s.length() > 50) {
      return '"' + s.substring(0, 50) + "...\"";
    }
    if (obj instanceof String s) {
      return '"' + s + '"';
    }
    if (obj instanceof Collection<?> c) {
      return "Collection (size = " + c.size() + ")";
    }
    return obj.toString();
  }

  String complex(Object obj1, Object obj2) {
    while(true) {
      try {
        if (obj1 instanceof String s) {
          while (true) {
            if (s.startsWith("a")) {
              return s;
            }
          }
        } else if (obj2 instanceof Collection<?> c) {
          return c.toString();
        }
      } catch (Exception e) {
        if (obj2 instanceof String s) {
          while (true) {
            if (s.startsWith("b")) {
              return s + "b";
            }
          }
        } else if (obj2 instanceof List<?> l) {
          return getStr() + l.size();
        }
      }
    }
  }

  String getStr() {
    return null;
  }
}
