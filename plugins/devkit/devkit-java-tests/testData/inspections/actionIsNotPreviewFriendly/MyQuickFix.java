import com.intellij.codeInspection.*;
import java.util.*;

class MyQuickFix implements LocalQuickFix {
  String test;
  int data[];
  boolean b1;
  <warning descr="Unnecessary @SafeFieldForPreview annotation: the field type is considered to be safe already">@SafeFieldForPreview</warning> boolean b2;
  Class<?> cls;
  List<?> <warning descr="Field may prevent intention preview from working properly">list</warning>;
  @SafeFieldForPreview
  List<?> safeList;

  Custom1 c11;
  <warning descr="Unnecessary @SafeFieldForPreview annotation: the field type is considered to be safe already">@SafeFieldForPreview</warning>
  Custom1 c12;
  Custom1[] c13;
  Custom2 <warning descr="Field may prevent intention preview from working properly">c21</warning>;
  @SafeFieldForPreview
  Custom2 c22;

  @SafeTypeForPreview
  static class Custom1 {}

  static class Custom2 {}
}