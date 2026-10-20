import org.intellij.lang.annotations.Language;

class CompilationUtil {
  public static final String DEFAULT_EXTENSION = "hx";
  {
    @Language("RegExp")
    String s = "\\.\\." + DEFAULT_EXTENSION;
  }
}