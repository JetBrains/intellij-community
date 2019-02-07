import org.intellij.lang.annotations.Pattern;

public class EnclosingClass {
  public static Object enclosingStatic() {
    return new Object() {
      public void foo(@Pattern("\\d+") String s) { }
    };
  }

  public Object enclosingInstance() {
    return new Object() {
      public boolean foo(@Pattern("\\d+") String s) {
        return s.contains(EnclosingClass.this.toString());
      }
    };
  }
}