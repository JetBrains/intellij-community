import org.intellij.lang.annotations.Pattern;

public class CapturedParam {
  public static void capturedParam(String s) {
    new CapturedParam().capturedParamHelper(s, "-", 0, "-");
  }

  private void capturedParamHelper(String s1, String s2, int i3, String s4) {
    class Local {
      final String f;

      Local(@Pattern("\\d+") String s) {
        f = s + s2 + i3 + s4;
      }
    }

    new Local(s1);
  }
}