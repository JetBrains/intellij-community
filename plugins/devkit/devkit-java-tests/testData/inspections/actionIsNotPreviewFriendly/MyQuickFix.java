import com.intellij.codeInspection.*;
import java.util.*;

class MyQuickFix implements LocalQuickFix {
  String test;
  int data[];
  boolean b1;
  Class<?> cls;
  List<?> <warning descr="Field may prevent intention preview from working properly">list</warning>;
  @SafeFieldForPreview
  List<?> safeList;
}