package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class AccessToNonThreadSafeStaticFieldFromInstanceInspectionTest extends LightInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package java.text;" +
      "import java.util.Date;" +
      "public abstract class DateFormat {" +
      "  public String format(Date d) {}" +
      "}",
      "package java.text;" +
      "public class SimpleDateFormat extends DateFormat {" +
      "  public SimpleDateFormat(String s) {}" +
      "}"
    };
  }

  public void testSimple() {
    doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class C {" +
           "    private static final SimpleDateFormat df = new SimpleDateFormat(\"yyyy-MM-dd\");" +
           "    private String s = /*Access to non thread-safe static field 'df' of type 'java.text.SimpleDateFormat'*/df/**/.format(new Date());" +
           "}");
  }

  public void testDeepCheck() {
    doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class C {" +
           "    private static final DateFormat df = new SimpleDateFormat(\"yyyy-MM-dd\");" +
           "    private static final Date d = new Date();" +
           "    private static final String s1 = df.format(d);" +
           "    String m() {" +
           "      return /*Access to non thread-safe static field 'df' of type 'java.text.SimpleDateFormat'*/df/**/.format(d);" +
           "    }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new AccessToNonThreadSafeStaticFieldFromInstanceInspection();
  }
}