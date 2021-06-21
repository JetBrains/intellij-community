package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class CatchMayIgnoreExceptionInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    final CatchMayIgnoreExceptionInspection tool = new CatchMayIgnoreExceptionInspection();
    tool.m_ignoreCatchBlocksWithComments = true;
    tool.m_ignoreNonEmptyCatchBlock = false;
    return tool;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework; public abstract class TestCase {}"
    };
  }

  public void testSimple() {
    doStatementTest("    try {\n" +
           "      throw new Exception();\n" +
           "    } /*Empty 'catch' block*/catch/**/ (Exception e) {\n" +
           "}\n");
  }

  public void testEmptyStatement() {
    doStatementTest(
           "    try {\n" +
           "      throw new Exception();\n" +
           "    } /*Empty 'catch' block*/catch/**/ (Exception e) {\n" +
           "      ;\n" +
           "    }\n");
  }

  public void testComment() {
    doStatementTest("    try {\n" +
           "      throw new Exception();\n" +
           "    } catch (Exception e) {\n" +
           "      // comment\n" +
           "    }\n");
  }

  public void testIgnored() {
    doTest("class C {\n" +
           "  void m() {\n" +
           "    try {\n" +
           "      throw new Exception();\n" +
           "    } catch (Exception ignored) {\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
  }

  public void testUnused() {
    doStatementTest("    try {\n" +
                    "    }\n" +
                    "    catch (RuntimeException /*Unused 'catch' parameter 'e'*/e/**/) {\n" +
                    "      System.out.println(\"oops something wrong\");\n" +
                    "    }\n" +
                    "  ");

  }

  public void testUsedIgnored() {
    doStatementTest("    try {\n" +
                    "    }\n" +
                    "    catch (RuntimeException /*'catch' parameter named 'ignore' is used*/ignore/**/) {\n" +
                    "      //comment\n" +
                    "      ignore.printStackTrace();\n" +
                    "    }\n" +
                    "  ");

  }

  public void testUnusedComment() {
    doStatementTest("    try {\n" +
                    "    }\n" +
                    "    catch (RuntimeException e) {  \n" +
                    "      // we do not use exception object for a reason\n" +
                    "      System.out.println(\"something wrong\");\n" +
                    "    }\n" +
                    "  ");
  }

  public void testVMExceptionIgnored() {
    doStatementTest("\n" +
                    "    try {\n" +
                    "      System.out.println(\"hello\");\n" +
                    "    }\n" +
                    "    /*Unexpected VM exception like 'java.lang.NullPointerException' might be ignored in a 'catch' block*/catch/**/(Exception ex) {\n" +
                    "      if(ex instanceof ClassCastException) {\n" +
                    "        // report invalid cast\n" +
                    "        ex.printStackTrace();\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ");
  }

  public void testVMExceptionIgnored2() {
    doStatementTest("\n" +
                    "    try {\n" +
                    "      System.out.println(\"hello\");\n" +
                    "    }\n" +
                    "    /*Unexpected VM exception like 'java.lang.NullPointerException' might be ignored in a 'catch' block*/catch/**/(Exception ex) {\n" +
                    "      if(ex.getCause() instanceof ClassCastException) {\n" +
                    "        // report invalid cast\n" +
                    "        ex.printStackTrace();\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ");
  }

  public void testVMExceptionIgnored3() {
    doStatementTest("\n" +
                    "    try {\n" +
                    "      System.out.println(\"hello\");\n" +
                    "    }\n" +
                    "    /*Unexpected VM exception like 'java.lang.NullPointerException' might be ignored in a 'catch' block*/catch/**/(Exception ex) {\n" +
                    "      if(\"foo\".equals(ex.getMessage())) {\n" +
                    "        // report some exception ignoring others\n" +
                    "        ex.printStackTrace();\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ");
  }

  public void testVMExceptionNotIgnored() {
    doStatementTest("\n" +
                    "    try {\n" +
                    "      System.out.println(\"hello\");\n" +
                    "    }\n" +
                    "    catch(Exception ex) {\n" +
                    "      if(ex instanceof ClassCastException) {\n" +
                    "        // report invalid cast\n" +
                    "        ex.printStackTrace();\n" +
                    "      }\n" +
                    "      throw ex;\n" +
                    "    }\n" +
                    "  ");
  }

  public void testVMExceptionLeaked() {
    doTest("class Exc {\n" +
           "  volatile Throwable exception;\n" +
           "  void test() {\n" +
           "    //noinspection EmptyTryBlock\n" +
           "    try {\n" +
           "      //blah blah\n" +
           "    }\n" +
           "    catch (Throwable e) {\n" +
           "      exception = e;\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testSneakyThrow() {
    doTest("class Exc {\n" +
           "  Object apply() {\n" +
           "    try {\n" +
           "      return foo();\n" +
           "    } catch (Throwable t) {\n" +
           "      throw throwChecked(t);\n" +
           "    }\n" +
           "  }\n" +
           "  \n" +
           "  native Object foo() throws Exception;\n" +
           "  \n" +
           "  @SuppressWarnings(\"unchecked\")\n" +
           "  private static <T extends Throwable> RuntimeException throwChecked(Throwable t) throws T {\n" +
           "    throw (T) t;\n" +
           "  }\n" +
           "}\n");
  }

}
