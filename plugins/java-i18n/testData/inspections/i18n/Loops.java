import org.jetbrains.annotations.Nls;
import java.util.List;

public class Loops {
  public static void test(List<String> strings, List<@Nls String> nlsStrings) {
    for (String s : strings) {
      takeNls(<warning descr="Reference to non-localized string is used where localized string is expected">s</warning>);
    }
    for (@Nls String s : strings) {
      takeNls(s);
    }
    strings.forEach(s -> {
      takeNls(<warning descr="Reference to non-localized string is used where localized string is expected">s</warning>);
    });

    for (String s : nlsStrings) {
      takeNls(<warning descr="Reference to non-localized string is used where localized string is expected">s</warning>);
    }
    for (@Nls String s : nlsStrings) {
      takeNls(s);
    }
    nlsStrings.forEach(s -> {
      takeNls(s);
    });
  }

  private static void takeNls(@Nls String localized) {}
}
