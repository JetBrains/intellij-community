import org.intellij.lang.annotations.Pattern;

public class LongParam {
  public void longParam(long l, @Pattern("\\d+") String s) { }
}