import com.intellij.codeInsight.intention.*;

import java.util.*;

class MyIntentionAction implements IntentionAction {
  String s;
  List<?> <warning descr="Field may prevent intention preview from working properly">list</warning>;
}