/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class StructuralSearchTest extends StructuralSearchTestCase {
  private static final String s1 = "class X {{" +
                                   "debug(\"In action performed:\"+event);"+
                                   "project = (Project)event.getDataContext().getData(DataConstants.PROJECT);" +
                                   "CodeEditorManager.getInstance(project).commitAllToPsiFile();" +
                                   "file = (PsiFile) event.getDataContext().getData(\"psi.File\"); " +
                                   "((dialog==null)?" +
                                   "  (dialog = new SearchDialog()):" +
                                   "  dialog" +
                                   ").show();" +
                                   "}}";

  private static final String s2 = "class X {{ ((dialog==null)? (dialog = new SearchDialog()): dialog).show(); }}";

  private static final String s4 = " do { " +
                                   "  pattern = pattern.getNextSibling(); " +
                                   " } " +
                                   " while (pattern!=null && filterLexicalNodes(pattern));";

  private static final String s5 = "class X {" +
                                   "{ System.out.println();" +
                                   "  while(false) { " +
                                   "    do { " +
                                   "       pattern = pattern.getNextSibling(); " +
                                   "    } " +
                                   "      while (pattern!=null && filterLexicalNodes(pattern)); " +
                                   "  } " +
                                   " do { " +
                                   "  pattern = pattern.getNextSibling(); " +
                                   " } while (pattern!=null && filterLexicalNodes(pattern));" +
                                   " { { " +
                                   "   do { " +
                                   "     pattern = pattern.getNextSibling(); " +
                                   "   } while (pattern!=null && filterLexicalNodes(pattern));" +
                                   " } }" +
                                   "}" +
                                   "}";

  private static final String s6 = " do { " +
                                   "  pattern.getNextSibling(); " +
                                   " } " +
                                   " while (pattern!=null && filterLexicalNodes(pattern));";

  private static final String s7 = "class X {{" +
                                   " if (true) throw new UnsupportedPatternException(statement.toString());" +
                                   " if (true) { " +
                                   "   throw new UnsupportedPatternException(statement.toString());" +
                                   " }" +
                                   "}}";

  private static final String s8 = " if (true) { " +
                                   "   throw new UnsupportedPatternException(statement.toString());" +
                                   " } ";

  private static final String s9 = " if (true) throw new UnsupportedPatternException(statement.toString());";

  private static final String s10 = "listener.add(new Runnable() { public void run() {} });";

  private static final String s12 = "class X {{" +
                                    "new Runnable() {" +
                                    "  public void run() {" +
                                    "   matchContext.getSink().matchingFinished();" +
                                    "   } " +
                                    "}" +
                                    "}}";

  private static final String s14_1 = "class X {{ if (true) { aaa(var); }}}";
  private static final String s14_2 = "class X {{ if (true) { aaa(var); bbb(var2); }\n if(1==1) { system.out.println('o'); }}}";
  private static final String s17 = "class X {{" +
                                    "token.getText().equals(token2.getText());" +
                                    "token.getText().equals(token2.getText2());" +
                                    "token.a.equals(token2.b);" +
                                    "token.a.equals(token2.a);" +
                                    "}}";
  private static final String s19 = "class X {{ Aaa a = (Aaa)b; Aaa c = (Bbb)d; }}";

  private static final String s22 = "class X {{ Aaa a = (Aaa)b; Bbb c = (Bbb)d; }}";

  private static final String s23 = "class X {{ a[i] = 1; b[a[i]] = f(); if (a[i]==1) return b[c[i]]; }}";
  private static final String s25  = "class MatcherImpl {  void doMatch(int a) {} }\n" +
                                     "class Matcher { abstract void doMatch(int a);}\n " +
                                     "class Matcher2Impl { void doMatch(int a, int b) {} } ";
  private static final String s27 = "class A {} interface B {}";

  private static final String s29 = "class A { void B(int C) {} } class D { void E(double e) {} }";

  private static final String s31 = "class A extends B { } class D extends B { } class C extends C {}";

  private static final String s33 = "class A implements B,C { } class D implements B,D { } class C2 implements C,B {}";

  private static final String s35 = "class A { int b; double c; void d() {} int e() {} } " +
                                    "class A2 { int b; void d() {} }";

  private static final String s37 = "class A { void d() throws B,C,D {} } class A2 { void d() throws B,C {} }";

  private static final String s39 = "class A extends B { } class A2 {  }";

  private static final String s41 = "class A extends B { int a = 1; } class B { int[] c= new int[2]; } " +
                                    "class D { double e; } class E { int d; } ";

  private static final String s43 = "interface A extends B { int B = 1; } " +
                                    "interface D { public final static double e = 1; } " +
                                    "interface E { final static ind d = 2; } " +
                                    "interface F {  } ";
  private static final String s45 = "class A extends B { private static final int B = 1; } " +
                                    "class C extends D { int B = 1; }" +
                                    "class E { }";

  private static final String s47 = "class C { java.lang.String t; } class B { BufferedString t2;} class A { String p;} ";

  private static final String s49 = "class C { void a() throws java.lang.RuntimeException {} } class B { BufferedString t2;}";

  private static final String s51 = "class C extends B { } class B extends A { } class E {}";

  private static final String s53 = "class C { " +
                                    "   String a = System.getProperty(\"abcd\"); " +
                                    "  static { String s = System.getProperty(a); }" +
                                    "  static void b() { String s = System.getProperty(a); }" +
                                    " }";

  private static final String s55 = "class X {{" +
                                    "  a = b.class;" +
                                    "}}";

  private static final String s57 = "/** @author Maxim */ class C {" +
                                    "  private int value; " +
                                    "} " +
                                    "class D {" +
                                    "  /** @serializable */ private int value;" +
                                    "private int value2; " +
                                    "  /** @since 1.4 */ void a() {} "+
                                    "}" +
                                    "class F { " +
                                    "  /** @since 1.4 */ void a() {} "+
                                    "  /** @serializable */ private int value2; " +
                                    "}" +
                                    "class G { /** @param a*/ void a() {} }";

  private static final String s59 = "interface A { void B(); }";

  private static final String s61 = "class X {{ a=b; c=d; return; } { e=f; } {}}";

  private static final String s63 = " class A { A() {} } class B { public void run() {} }";
  private static final String s63_2 = " class A { A() {} " +
                                      "class B { public void run() {} } " +
                                      "class D { public void run() {} } " +
                                      "} " +
                                      "class C {}";

  private static final String s65 = "class X {{ if (A instanceof B) {} else if (B instanceof C) {}}}";

  private static final String s67 = "class X {{ buf.append((VirtualFile)a); }}";

  private static final String s69 = "class X {{ System.getProperties(); System.out.println(); java.lang.System.out.println(); some.other.System.out.println(); }}";

  private static final String s71 = " class A { " +
                                    "class D { D() { c(); } }" +
                                    "void a() { c(); new MouseListener() { void b() { c(); } } }" +
                                    " }";

  private static final String s73 = " class A { int A; static int B=5; public abstract void a(int c); void q() { ind d=7; } }";
  private static final String s75 = "/** @class aClass\n @author the author */ class A {}\n" +
                                    "/** */ class B {}\n" +
                                    "/** @class aClass */ class C {}";

  private static final String s77 = " new ActionListener() {} ";

  private static final String s79 = " class A { static { int c; } void a() { int b; b=1; }} ";

  private static final String s81 = "class Pair<First,Second> {" +
                                    "  <C,F> void a(B<C> b, D<F> e) throws C {" +
                                    "    P<Q> r = (S<T>)null;"+
                                    "    Q q = null; "+
                                    "    if (r instanceof S<T>) {}"+
                                    "  } " +
                                    "} class Q { void b() {} } ";

  private static final String s81_2 = "class Double<T> {} class T {} class Single<First extends A & B> {}";


  private static final String s83 = "/**\n" +
                                    " * @hibernate.class\n" +
                                    " *  table=\"CATS\"\n" +
                                    " */\n" +
                                    "public class Cat {\n" +
                                    "    private Long id; // identifier\n" +
                                    "    private Date birthdate;\n" +
                                    "    /**\n" +
                                    "     * @hibernate.id\n" +
                                    "     *  generator-class=\"native\"\n" +
                                    "     *  column=\"CAT_ID\"\n" +
                                    "     */\n" +
                                    "    public Long getId() {\n" +
                                    "        return id;\n" +
                                    "    }\n" +
                                    "    private void setId(Long id) {\n" +
                                    "        this.id=id;\n" +
                                    "    }\n" +
                                    "\n" +
                                    "    /**\n" +
                                    "     * @hibernate.property\n" +
                                    "     *  column=\"BIRTH_DATE\"\n" +
                                    "     */\n" +
                                    "    public Date getBirthdate() {\n" +
                                    "        return birthdate;\n" +
                                    "    }\n" +
                                    "    void setBirthdate(Date date) {\n" +
                                    "        birthdate = date;\n" +
                                    "    }\n" +
                                    "    /**\n" +
                                    "     * @hibernate.property\n" +
                                    "     *  column=\"SEX\"\n" +
                                    "     *  not-null=\"true\"\n" +
                                    "     *  update=\"false\"\n" +
                                    "     */\n" +
                                    "    public char getSex() {\n" +
                                    "        return sex;\n" +
                                    "    }\n" +
                                    "    void setSex(char sex) {\n" +
                                    "        this.sex=sex;\n" +
                                    "    }\n" +
                                    "}";




  private static final String s85 = "class X {{ int a; a=1; a=1; return a; }}";
  private static final String s86 = "'T; 'T;";

  private static final String s89 = "class X {{ a = 1; b = 2; c=3; }}";

  private static final String s91 = "class a {\n" +
                                    "  void b() {\n" +
                                    "    int c;\n" +
                                    "\n" +
                                    "    c = 1;\n" +
                                    "    b();\n" +
                                    "    a a1;\n" +
                                    "  }\n" +
                                    "}";

  private static final String s93 = " class A {" +
                                    "private int field;" +
                                    "public void b() {}" +
                                    "}";

  private static final String s95 = " class Clazz {" +
                                    "private int field;" +
                                    "private int field2;" +
                                    "private int fiel-d2;" +
                                    "}";

  public void testSearchExpressions() {
    assertFalse("subexpr match", findMatchesCount(s2, "dialog = new SearchDialog()") == 0);
    assertEquals("search for new ", 0, findMatchesCount(s10, " new XXX()"));
    assertEquals("search for anonymous classes", 1, findMatchesCount(s12, "new Runnable() {}"));
    assertEquals("expr in def initializer", 3, findMatchesCount(s53, "System.getProperty('T)"));

    assertEquals("a.class expression", 1, findMatchesCount(s55, "'T.class"));

    String complexCode = "class X {{" +
                         "interface I { void b(); } interface I2 extends I {} class I3 extends I {} " +
                         "class A implements I2 {  void b() {} } class B implements I3 { void b() {}} " +
                         "I2 a; I3 b; a.b(); b.b(); b.b(); A c; B d; c.b(); d.b(); d.b();" +
                         "}}";

    assertEquals("expr type condition", 1, findMatchesCount(complexCode, "'t:[exprtype( I2 )].b();"));
    assertEquals("expr type condition 2", 5, findMatchesCount(complexCode, "'t:[!exprtype( I2 )].b();"));
    assertEquals("expr type condition 3", 2, findMatchesCount(complexCode, "'t:[exprtype( *I2 )].b();"));
    assertEquals("expr type condition 4", 4, findMatchesCount(complexCode, "'t:[!exprtype( *I2 )].b();"));

    String complexCode2 = "enum X { XXX, YYY }\n class C { static void ordinal() {} void test() { C c; c.ordinal(); c.ordinal(); X.XXX.ordinal(); } }";
    assertEquals("expr type condition with enums", 1,
                 findMatchesCount(complexCode2, "'t:[exprtype( *java\\.lang\\.Enum )].ordinal()"));

    final String in = "class X {{" +
                      "processInheritors(1,2,3,4); " +
                      "processInheritors(1,2,3); " +
                      "processInheritors(1,2,3,4,5,6);" +
                      "}}";
    assertEquals("no smart detection of search target", 3,
                 findMatchesCount(in, "'instance?.processInheritors('_param1{1,6});"));

    String arrays = "class X {{" +
                    "int[] a = new int[20];\n" +
                    "byte[] b = new byte[30]" +
                    "}}";
    assertEquals("Improper array search", 1, findMatchesCount(arrays, "new int['_a]"));

    String someCode = "class X {{ a *= 2; a+=2; }}";
    assertEquals("Improper *= 2 search", 1, findMatchesCount(someCode, "a *= 2;"));

    String s1 = "class X {{" +
                "Thread t = new Thread(\"my thread\",\"my another thread\") {\n" +
                "    public void run() {\n" +
                "        // do stuff\n" +
                "    }\n" +
                "}" +
                "}}";
    assertEquals("Find inner class parameters", 2, findMatchesCount(s1, "new Thread('args*) { '_Other* }"));

    String s3 = "class X {{" +
                "Thread t = new Thread(\"my thread\") {\n" +
                "    public void run() {\n" +
                "        // do stuff\n" +
                "    }\n" +
                "};" +
                "}}";
    assertEquals("Find inner class by new", 1, findMatchesCount(s3, "new Thread('_args)"));

    String s5 = "class A {\n" +
                "public static <T> T[] copy(T[] array, Class<T> aClass) {\n" +
                "    int i = (int)0;\n" +
                "    int b = (int)0;\n" +
                "    return (T[])array.clone();\n" +
                "  }\n" +
                "}";
    assertEquals("Find cast to array", 1, findMatchesCount(s5, "('_T[])'_expr"));

    String s7 = "import java.math.BigDecimal;\n" +
                "\n" +
                "public class Prorator {\n" +
                "        public void prorate(BigDecimal[] array) {\n" +
                "                // do nothing\n" +
                "        }\n" +
                "        public void prorate2(java.math.BigDecimal[] array) {\n" +
                "                // do nothing\n" +
                "        }\n" +
                "        public void prorate(BigDecimal bd) {\n" +
                "                // do nothing\n" +
                "        }\n" +
                "\n" +
                "        public static void main(String[] args) {\n" +
                "                BigDecimal[] something = new BigDecimal[2];\n" +
                "                java.math.BigDecimal[] something2 = new BigDecimal[2];\n" +
                "                something[0] = new BigDecimal(1.0);\n" +
                "                something[1] = new BigDecimal(1.0);\n" +
                "\n" +
                "                Prorator prorator = new Prorator();\n" +
                "\n" +
                "// ---------------------------------------------------\n" +
                "// the line below should've been found, in my opinion.\n" +
                "// --------------------------------------------------\n" +
                "                prorator.prorate(something);\n" +
                "                prorator.prorate(something2);\n" +

                "                prorator.prorate(something[0]);\n" +
                "                prorator.prorate(something[1]);\n" +
                "                prorator.prorate(something[0]);\n" +
                "        }\n" +
                "}";
    assertEquals("Find method call with array for parameter expr type", 2,
                 findMatchesCount(s7, "'_Instance.'_MethodCall:[regex( prorate )]('_Param:[exprtype( BigDecimal\\[\\] )]) "));

    String s13 = "class X {{ try { } catch(Exception e) { e.printStackTrace(); }}}";
    assertEquals("Find statement in catch", 1, findMatchesCount(s13, "'_Instance.'_MethodCall('_Parameter*)"));

    String s9 = "class X {{" +
                "int a[] = new int[] { 1,2,3,4};\n" +
                "int b[] = { 2,3,4,5 };\n" +
                "Object[] c = new Object[] { \"\", null};\n" +
                "Object[] d = {null, null};\n" +
                "Object[] e = {};\n" +
                "Object[] f = new Object[]{}\n" +
                "String[] g = new String[]{}\n" +
                "String[] h = new String[]{new String()}" +
                "}}";

    assertEquals("Find new array expressions, but no array initializer expressions", 5,
                 findMatchesCount(s9, "new '_ []{ '_* }"));

    assertEquals("Find new int array expressions, including array initializer expressions", 2,
                 findMatchesCount(s9, "new int []{ '_* }"));

    assertEquals("Find new int array expressions, including array initializer expressions using variable ", 2,
                 findMatchesCount(s9, "new 'a?:int [] { '_* }"));

    assertEquals("Find all new array expressions, including array initializers", 8,
                 findMatchesCount(s9, "new '_? []{ '_* }"));

    assertEquals("Find new Object array expressions, including array initializer expressions", 4,
                 findMatchesCount(s9, "new Object[] { '_* }"));

    assertEquals("Find only array initializer expressions", 3,
                 findMatchesCount(s9, "new '_{0,0}[] { '_* }"));

    assertEquals("Find only int array initializer expressions", 1,
                 findMatchesCount(s9, "new '_{0,0}:int [] { '_* }"));

    assertEquals("Try to find String array initializer expressions", 0,
                 findMatchesCount(s9, "new '_{0,0}:String [] { '_* }"));

    String s10 = "class X {{" +
                 "int time = 99;\n" +
                 "String str = time < 0 ? \"\" : \"\";" +
                 "String str2 = time < time ? \"\" : \"\";" +
                 "}}";

    assertEquals("Find expressions mistaken for declarations by parser in block mode", 1,
                 findMatchesCount(s10, "time < time"));

    assertEquals("Find expressions mistaken for declarations by parser in block mode 2", 1,
                 findMatchesCount(s10, "time < 0"));

    assertEquals("Find expressions mistaken for declarations by parser in block mode 3", 1,
                 findMatchesCount(s10, "time < 0 ? '_a : '_b"));

    assertEquals("Find expressions mistaken for declarations by parser in block mode 4", 2,
                 findMatchesCount(s10, "'_a < '_b"));
  }

  public void testLiteral() {
    String s = "class A {\n" +
               "  static String a = 1;\n" +
               "  static String s = \"aaa\";\n" +
               "  static String s2;\n" +
               "}";
    assertEquals("Literal", 2, findMatchesCount(s, "static String '_FieldName = '_Init?:[!regex( \".*\" )];"));
    assertEquals("Literal, 2", 1, findMatchesCount(s, "static String '_FieldName = '_Init:[!regex( \".*\" )];"));
    assertEquals("String literal", 1, findMatchesCount(s, "\"'String\""));

    String source = "@SuppressWarnings(\"test\") class A {" +
                    "  @SuppressWarnings({\"other\", \"test\"}) String field;" +
                    "}";
    assertEquals("String literal in annotation", 2, findMatchesCount(source, "\"test\""));

    String s2 = "class A {" +
                "  String a = \"Alpha\";" +
                "  String b = \"Bravo\";" +
                "  String c = \"Charlie\";" +
                "}";
    assertEquals("match literal contents", 1, findMatchesCount(s2, "\"'String:[regex( alpha )]\""));
    assertEquals("negate match literal contents", 2, findMatchesCount(s2, "\"'String:[!regex( alpha )]\""));
    assertEquals("match literal contents and all types", 1, findMatchesCount(s2, "\"'String:[regex( alpha ) && exprtype( .* )]\""));
  }

  public void testCovariantArraySearch() {
    String s1 = "class X {{ String[] argv; }}";
    String s2 = "class X {{ String argv; }}";
    assertEquals("Find array types", 0, findMatchesCount(s1, "String argv;"));
    assertEquals("Find array types, 2", 0, findMatchesCount(s2, "String[] argv;"));
    assertEquals("Find array types, 3", 0, findMatchesCount(s2, "'T[] argv;"));
    assertEquals("Find array types, 3", 1, findMatchesCount(s1, "'T:*Object [] argv;"));

    String s11 = "class A {\n" +
                 "  void main(String[] argv);" +
                 "  void main(String argv[]);" +
                 "  void main(String argv);" +
                 "}";
    assertEquals("Find array covariant types", 2, findMatchesCount(s11, "'_t:[regex( *Object\\[\\] ) ] '_t2;"));
    assertEquals("Find array covariant types, 2", 2, findMatchesCount(s11, "'_t:[regex( *Object ) ] '_t2 [];"));
    assertEquals("Find array covariant types, 3", 1, findMatchesCount(s11, "'_t:[regex( *Object ) ] '_t2;"));
  }

  public void testFindArrayDeclarations() {
    String source = "class A {" +
                    "  String ss[][];" +
                    "  int f()[] {" +
                    "    return null;" +
                    "  }" +
                    "}";

    String target = "String[][] '_s;";
    assertEquals("should find multi-dimensional c-style array declarations", 1, findMatchesCount(source, target));

    String target2 = "class '_A { int[] 'f(); }";
    assertEquals("should find c-style method return type declarations", 1, findMatchesCount(source, target2));

    String target3 = "class '_A { int 'f(); }";
    assertEquals("should not find methods with array return types",0, findMatchesCount(source, target3));

    String source2 = "class A {" +
                     "  void y(int... i) {}" +
                     "  void y(String... ss) {}" +
                     "  void y(boolean b) {}" +
                     "}";
    assertEquals("find ellipsis type 1", 1, findMatchesCount(source2, "String[] '_a;"));
    assertEquals("find ellipsis type 2", 1, findMatchesCount(source2, "int[] '_a;"));
    assertEquals("find ellipsis type 3", 1, findMatchesCount(source2, "class '_X { void '_m(int... '_a); }"));
    assertEquals("find ellipsis type 4", 2, findMatchesCount(source2, "'_T[] '_a;"));

    String source3 = "class A {" +
                     "  private int[] is;" +
                     "}";
    assertEquals("find primitive array 1", 1, findMatchesCount(source3, "int[] '_a;"));
    assertEquals("find primitive array 2", 1, findMatchesCount(source3, "'_T[] '_a;"));
    assertEquals("find primitive array 3", 1, findMatchesCount(source3, "'_T:[regex( int )][] '_a;"));
    assertEquals("find primitive array 4", 1, findMatchesCount(source3, "'_T:[regex( int\\[\\] )] '_a;"));
  }

  // @todo support back references (\1 in another reg exp or as field member)
  //private static final String s1002 = " setSSS( instance.getSSS() ); " +
  //                                    " setSSS( instance.SSS ); ";
  //private static final String s1003 = " 't:set(.+) ( '_.get't_1() ); ";
  //private static final String s1003_2 = " 't:set(.+) ( '_.'t_1 ); ";

  public void testSearchStatements() {
    assertEquals("statement search", 1, findMatchesCount(s1, "((dialog==null)? (dialog = new SearchDialog()): dialog).show();"));
    assertEquals("several constructions match", 3, findMatchesCount(s5, s4));
    assertFalse("several constructions 2",(findMatchesCount(s5,s6))!=0);

    assertEquals("several constructions 3", 2, findMatchesCount(s7, s8));
    assertEquals("several constructions 4", 2, findMatchesCount(s7, s9));

    final String s1000 = "class X {" +
                         "    { lastTest = \"search for parameterized pattern\";\n" +
                         "      matches = testMatcher.findMatches(s14_1,s15, options);\n" +
                         "      if (matches.size()!=2 ) return false;\n" +
                         "lastTest = \"search for parameterized pattern\";\n" +
                         "      matches = testMatcher.findMatches(s14_1,s15, options);\n" +
                         "      if (matches.size()!=2 ) return false; }" +
                         "}";
    final String s1001 = "lastTest = '_Descr; " +
                         "      matches = testMatcher.findMatches('_In,'_Pattern, options);\n" +
                         "      if (matches.size()!='_Number ) return false;";

    assertEquals("several operators 5", 2, findMatchesCount(s1000, s1001));
    assertEquals("two the same statements search", 1, findMatchesCount(s85,s86));

    final String s87 = "class X {{ getSomething(\"2\"); getSomething(\"1\"); a.call(); }}";
    assertEquals("search for simple call", 1, findMatchesCount(s87, " '_Instance.'Call('_*); "));
    assertEquals("search for simple call 2", 3, findMatchesCount(s87, " 'Call('_*); "));
    assertEquals("search for simple call 3", 3, findMatchesCount(s87, " '_Instance?.'Call('_*); "));
    assertEquals("search for simple call 4", 2, findMatchesCount(s87, " '_Instance{0,0}.'Call('_*); "));

    String s10015 = "class X {{ DocumentListener[] listeners = getCachedListeners(); }}";
    assertEquals("search for definition with init", 1, findMatchesCount(s10015, "'_Type 'Var = '_Call();"));

    String s10017 = "class X {{ a = b; b = c; a=a; c=c; }}";
    assertEquals("search silly assignments", 2, findMatchesCount(s10017, "'_a = '_a;"));

    String s10019 = "class X {{ a.b(); a.b(null); a.b(null, 1); }}";
    assertEquals("search parameter", 1, findMatchesCount(s10019, "a.b(null);"));

    String s1008 = "class X {{ int a, b, c, d; int a,b,c; int c,d; int e; }}";
    assertEquals("search many declarations", 2, findMatchesCount(s1008, "int '_a{3,4};"));

    String s1 = "class X {{ super(1,1);  call(1,1); call(2,2); }}";
    assertEquals("search super", 1, findMatchesCount(s1, "super('_t*);"));

    String s10021 = "class X {{" +
                    "short a = 1;\n" +
                    "short b = 2;\n" +
                    "short c = a.b();" +
                    "}}";

    assertEquals("search def init bug", 1, findMatchesCount(s10021, "short '_a = '_b.b();"));

    String s10023 = "class X {{" +
                    "abstract class A { public abstract short getType(); }\n" +
                    "A a;\n" +
                    "switch(a.getType()) {\n" +
                    "  default:\n" +
                    "  return 0;\n" +
                    "}\n" +
                    "switch(a.getType()) {\n" +
                    "  case 1:\n" +
                    "  { return 0; }\n" +
                    "}" +
                    "}}";
    assertEquals("finding switch", 2,
                 findMatchesCount(s10023, "switch('_a:[exprtype( short )]) { '_statement*; }"));

    String s10025 = "class X {{" +
                    "A[] a;\n" +
                    "A b[];\n" +
                    "A c;" +
                    "}}";
    assertEquals("array types in dcl", 2, findMatchesCount(s10025, "A[] 'a;"));
    assertEquals("array types in dcl 2", 2, findMatchesCount(s10025, "A 'a[];"));

    String s10027 = "class X {{" +
                    "try { a(); } catch(Exception ex) {}\n" +
                    "try { a(); } finally {}\n" +
                    "try { a(); } catch(Exception ex) {} finally {}\n" +
                    "}}";
    assertEquals("finally matching", 2, findMatchesCount(s10027, "try { a(); } finally {}\n"));

    String s10029 = "class X {{ for(String a:b) { System.out.println(a); }}}";
    assertEquals("for each matching", 1, findMatchesCount(s10029, "for(String a:b) { '_a; }"));

    String s10031 = "class X {{" +
                    "try { a(); } catch(Exception ex) {} catch(Error error) { 1=1; }\n" +
                    "try { a(); } catch(Exception ex) {}" +
                    "}}";
    assertEquals("finally matching", 2,
                 findMatchesCount(s10031, "try { a(); } catch('_Type+ 'Arg+) { '_Statements*; }\n"));

    String s10033 = "class X {{ " +
                    "return x;\n" +
                    "return !x;\n" +
                    "return (x);\n" +
                    "return (x);\n" +
                    "return !(x);" +
                    "}}";
    assertEquals("Find statement with parenthesized expr",2,findMatchesCount(s10033, "return ('a);"));
    assertEquals("Find statement ignoring parentheses expr 2", 3, findMatchesCount(s10033, "return 'a:[regex( x )];"));

    String in = "class X {{" +
                "if (true) {" +
                "  System.out.println();" +
                "} else {" +
                "  System.out.println();" +
                "}" +
                "if (true) System.out.println();" +
                "}}";
    assertEquals("Find if statement with else", 2, findMatchesCount(in, "if ('_exp) { '_statement*; }"));
    assertEquals("Find if statement without else", 1,
                 findMatchesCount(in, "if ('_exp) { '_statement*; } else { '_statement2{0,0}; }"));

    String in2 = "/**" +
                 " * javadoc" +
                 "*/" +
                 "class A {" +
                 "  /* comment */" +
                 "" +
                 "  void a() {" +
                 "    System.out.println();\n" +
                 "    // comment\n" +
                 "  }" +
                 "}";
    assertEquals("Should find statements and comments in statement context only", 2, findMatchesCount(in2, "'_statement;"));

    String in3 = "class X {{" +
                 "new Object().hashCode();" +
                 "new Object().toString();" +
                 "}}";
    assertEquals("Find typed expression statements", 1, findMatchesCount(in3, "'_expr:[exprtype( int )];"));
  }

  public void testSearchClass() {
    assertEquals("no modifier for interface vars", 3, findMatchesCount(s43, "interface '_ { '_T 'T2 = '_T3; } "));
    assertEquals("different order of access modifiers", 1,
                 findMatchesCount(s45, "class '_ { final static private '_T 'T2 = '_T3; } "));
    assertEquals("no access modifier", 2, findMatchesCount(s45, "class '_ { '_T 'T2 = '_T3; } "));
    assertEquals("type differs with package", 2, findMatchesCount(s47, "class '_ { String '_; }"));
    assertEquals("reference could differ in package", 1,
                 findMatchesCount(s49, "class '_ { '_ '_() throws RuntimeException; }"));

    String s51 = "class C extends java.awt.List {} class A extends java.util.List {} class B extends java.awt.List {} ";
    assertEquals("reference could differ in package 2", 2,
                 findMatchesCount(s51, "class 'B extends '_C:java\\.awt\\.List {}"));
    assertEquals("method access modifier", 0, findMatchesCount(s93, " class '_ {private void b() {}}"));
    assertEquals("method access modifier 2", 1, findMatchesCount(s93, " class '_ {public void b() {}}"));
    assertEquals("field access modifier", 0, findMatchesCount(s93, " class '_ {protected int field;}"));
    assertEquals("field access modifier 2", 1, findMatchesCount(s93, " class '_ {private int field;}"));

    final String s127 = "class a { void b() { new c() {}; } }";
    assertEquals("class finds anonymous class", 2, findMatchesCount(s127, "class 't {}"));

    final String s129 = "class a { public void run() {} }\n" +
                        "class a2 { public void run() { run(); } }\n" +
                        "class a3 { public void run() { run(); } }\n" +
                        "class a4 { public void run(); }";

    assertEquals("empty method finds empty method only", 1,
                 findMatchesCount(s129, "class 'a { public void run() {} }"));
    assertEquals("nonempty method finds nonempty method", 2,
                 findMatchesCount(s129, "class 'a { public void run() { '_statement; } }"));
    assertEquals("nonempty method finds nonempty method", 4,
                 findMatchesCount(s129, "class 'a { public void run(); }"));

    final String s133 = "class S {\n" +
                        "void cc() {\n" +
                        "        new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                f();\n" +
                        "            }\n" +
                        "            private void f() {\n" +
                        "                //To change body of created methods use File | Settings | File Templates.\n" +
                        "            }\n" +
                        "        };\n" +
                        "        new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                f();\n" +
                        "            }\n" +
                        "            private void g() {\n" +
                        "                //To change body of created methods use File | Settings | File Templates.\n" +
                        "            }\n" +
                        "        };\n" +
                        "        new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                f();\n" +
                        "            }\n" +
                        "        };\n" +
                        "    }\n" +
                        "    private void f() {\n" +
                        "        //To change body of created methods use File | Settings | File Templates.\n" +
                        "    }\n" +
                        "} ";
    final String s134 = "new Runnable() {\n" +
                        "            public void run() {\n" +
                        "                '_f ();\n" +
                        "            }\n" +
                        "            private void '_f ();\n" +
                        "        }";
    assertEquals(
      "complex expr matching",
      1,
      findMatchesCount(s133,s134)
    );

    final String s135 = "abstract class My {\n" +
                        "    abstract void f();\n" +
                        "}\n" +
                        "abstract class My2 {\n" +
                        "    abstract void f();\n" +
                        "    void fg() {}\n" +
                        "}";
    final String s136 = "class 'm {\n" +
                        "    void f();\n" +
                        "    '_type '_method{0,0} ('_paramtype* '_paramname* );\n" +
                        "}";
    assertEquals("reject method with 0 max occurence", 1, findMatchesCount(s135,s136));

    final String s137 = "abstract class My {\n" +
                        "  int a;\n" +
                        "}\n" +
                        "abstract class My2 {\n" +
                        "    Project b;\n" +
                        "}" +
                        "abstract class My3 {\n" +
                        "    Class clazz;"+
                        "    Project b = null;\n" +
                        "}" +
                        "abstract class My {\n" +
                        "  int a = 1;\n" +
                        "}\n";
    assertEquals("reject field with 0 max occurence", 2,
                 findMatchesCount(s137, "class 'm { Project '_f{0,0} = '_t?; }"));

    final String s139 = "class My { boolean equals(Object o); int hashCode(); }";
    final String s140 = "class 'A { boolean equals(Object '_o ); int '_hashCode{0,0}:hashCode (); }";
    assertEquals("reject method with constraint", 0, findMatchesCount(s139,s140));

    final String s139_2 = "class My { boolean equals(Object o); }";
    assertEquals("reject field with 0 max occurence", 1, findMatchesCount(s139_2,s140));

    final String s141 = "class A { static { a = 10 } }\n" +
                        "class B { { a = 10; } }\n" +
                        "class C { { a = 10; } }";
    assertEquals("static block search", 1, findMatchesCount(s141, "class '_ { static { a = 10; } } "));

    final String in = "class D<T> {}\n" +
                      "class T {}";
    assertEquals("search for class should not find type parameters", 2, findMatchesCount(in, "class 'A {}"));
  }

  public void testParameterlessConstructorSearch() {
    final String s143 = "class A { A() {} };\n" +
                        "class B { B(int a) {} };\n" +
                        "class C { C() {} C(int a) {} };\n" +
                        "class D {}\n" +
                        "class E {}";
    assertEquals("parameterless constructor search", 3,
                 findMatchesCount(s143, "class '_a { '_d{0,0}:[ script( \"__context__.constructor\" ) ]('_b+ '_c+); }"));
  }

  public void testScriptSearch() {
    final String source = "package a;" +
                          "class BX extends java.util.List {" +
                          "  private static final java.util.List VALUE = new BX();" +
                          "}" +
                          "class CX extends java.util.List {" +
                          "  private static final String S = \"\";" +
                          "}";
    // find static final fields whose type is a proper ancestor of the class declaring their fields
    assertEquals("all variables accessible from script", 1,
                 findMatchesCount(source,
                                  "[script(\""                                                         +
                                  "import com.intellij.psi.util.InheritanceUtil\n"                     +
                                  "import com.intellij.psi.util.PsiTreeUtil\n"                         +
                                  "import com.intellij.psi.PsiClass\n"                                 +
                                  "init != null &&"                                                    + // redundant reference to '_init
                                  "InheritanceUtil.isInheritor(\n"                                     +
                                  "        PsiTreeUtil.getParentOfType(variable, PsiClass.class),\n"   + // reference to 'variable
                                  "        true, \n"                                                   +
                                  "        Type.type.canonicalText\n"                                  + // reference to '_Type
                                  ")\n\")]"                                                            +
                                  "static final '_Type 'variable = '_init;"));

    final String source2 = "class A {" +
                           "  String s = new String();" +
                           "  @SuppressWarnings(\"\") int m() {" +
                           "    n();" +
                           "    int i = 2+1;" +
                           "    return i;" +
                           "  }" +
                           "  void n() {}" +
                           "}";
    assertEquals("type of variables in script are as expected", 1,
                 findMatchesCount(source2,
                                  "[script(\"" +
                                  "import com.intellij.psi.*\n" +
                                  "__context__ instanceof PsiElement &&" +
                                  "a instanceof PsiClass &&" +
                                  "b instanceof PsiTypeElement &&" +
                                  "c instanceof PsiField &&" +
                                  "d instanceof PsiNewExpression &&" +
                                  "e instanceof PsiTypeElement &&" +
                                  "f instanceof PsiMethod &&" +
                                  "g instanceof PsiTypeElement &&" +
                                  "h instanceof PsiLocalVariable &&" +
                                  "i instanceof PsiPolyadicExpression &&" +
                                  "j instanceof PsiReferenceExpression &&" +
                                  "k instanceof PsiMethodCallExpression &&" +
                                  "l instanceof PsiAnnotation\n" +
                                  "\")]" +
                                  "class '_a {" +
                                  "  '_b '_c = new '_d();" +
                                  "  @'_l '_e '_f() {" +
                                  "    '_k();" +
                                  "    '_g '_h = '_i;" +
                                  "    return '_j;" +
                                  "  }" +
                                  "}"));

    assertEquals("Current variable should be available under own name", 1,
                 findMatchesCount(source2, "'_a + '_b:[script(\"__log__.info(b)\n__log__.info(__context__)\ntrue\")]"));

    final String in = "class C {" +
                      "  {" +
                      "    int i = 0;" +
                      "    i += 1;" +
                      "    (i) = 3;" +
                      "    int j = i;" +
                      "    i();" +
                      "  }" +
                      "  void i() {}" +
                      "}";
    // existing pattern "fields/variables read"
    assertEquals("Find reads of symbol (including operator assignment)", 2,
                 findMatchesCount(in, "'_Symbol:[script(\"import com.intellij.psi.*\n" +
                                      "import static com.intellij.psi.util.PsiUtil.*\n" +
                                      "Symbol instanceof PsiReferenceExpression && isAccessedForReading(Symbol)\")]"));

    // existing pattern "fields/variables with given name pattern updated"
    assertEquals("Find writes of symbol", 3,
                 findMatchesCount(in, "'_Symbol:[regex( i ) && script(\"import com.intellij.psi.*\n" +
                                      "import static com.intellij.psi.util.PsiUtil.*\n" +
                                      "Symbol instanceof PsiExpression && isAccessedForWriting(Symbol) ||\n" +
                                      "  Symbol instanceof PsiVariable && Symbol.getInitializer() != null\")]"));
  }

  public void testCheckScriptValidation() {
    try {
      findMatchesCount("", "'_b:[script( \"^^^\" )]");
      fail("Validation does not work");
    } catch (MalformedPatternException ignored) {}
  }

  //public void testRelationBetweenVars() {
  //  final String s1 = "public class Foo {\n" +
  //                      "    public static final Logger log = Logger.getInstance(Foo.class);\n" +
  //                      "    public static final Logger log2 = Logger.getInstance(Foo2.class);\n" +
  //                      "    public static final Logger log3 = Logger.getInstance(Foo2.class);\n" +
  //                      "}";
  //  final String s2 = "class '_a { static Logger 'log+ = Logger.getInstance('_b:[script( \"_a != _b\" )].class); }";
  //  assertEquals(
  //    "relation between vars in script",
  //    2,
  //    findMatchesCount(s1,s2)
  //  );
  //}

  public void testExprTypeWithObject() {
    String s1 = "import java.util.*;\n" +
                "class A {\n" +
                "  void b() {\n" +
                "    Map map = new HashMap();" +
                "    class AppPreferences {}\n" +
                "    String key = \"key\";\n" +
                "    AppPreferences value = new AppPreferences();\n" +
                "    map.put(key, value );\n" +
                "    map.put(value, value );\n" +
                "    map.put(\"key\", value );\n" +
                "    map.put(\"key\", new AppPreferences());\n" +
                "  }\n" +
                "}";
    String s2 = "'_map:[exprtype( *java\\.util\\.Map )].put('_key:[ exprtype( *Object ) ], '_value:[ exprtype( *AppPreferences ) ]);";
    assertEquals("expr type with object", 4, findMatchesCount(s1, s2));
  }

  public void testInterfaceImplementationsSearch() {
    String in = "class A implements Cloneable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class B implements Serializable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class C implements Cloneable,Serializable {\n" +
                "    \n" +
                "  }\n" +
                "  class C2 implements Serializable,Cloneable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class E extends B implements Cloneable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class F extends A implements Serializable {\n" +
                "    \n" +
                "  }\n" +
                "  \n" +
                "  class D extends C {\n" +
                "    \n" +
                "  }";
    assertEquals("search interface within hierarchy", 5,
                 findMatchesCount(in, "class 'A implements '_B:*Serializable , '_C:*Cloneable {}"));
  }

  public void testSearchBacktracking() {
    assertEquals("backtracking greedy regexp", 1, findMatchesCount(s89, "{ '_T*; '_T2*; }"));
    assertEquals("backtracking greedy regexp 2", 1, findMatchesCount(s89, " { '_T*; '_T2*; '_T3+; } "));
    assertEquals("backtracking greedy regexp 3", 0, findMatchesCount(s89, " { '_T+; '_T2+; '_T3+; '_T4+; } "));
    assertEquals("counted regexp (with back tracking)", 1, findMatchesCount(s89, " { '_T{1,3}; '_T2{2}; } "));
    assertEquals("nongreedy regexp (counted, with back tracking)", 1,
                 findMatchesCount(s89, " { '_T{1}?; '_T2*?; '_T3+?; } "));
    assertEquals("nongreedy regexp (counted, with back tracking) 2", 0,
                 findMatchesCount(s89, " { '_T{1}?; '_T2{1,2}?; '_T3+?; '_T4+?; } "));

    String s1000 = "class A {\n" +
                   "      void _() {}\n" +
                   "      void a(String in, String pattern) {}\n" +
                   "    }";
    String s1001 = "class '_Class { \n" +
                   "  '_ReturnType+ 'MethodName+ ('_ParameterType* '_Parameter* );\n" +
                   "}";
    assertEquals("handling of no match", 2, findMatchesCount(s1000,s1001));
  }

  public void testSearchSymbol() {
    final String s131 = "class X {{ a.b(); c.d = 1; }}";
    assertEquals("symbol match", 2, findMatchesCount(s131, "'T:b|d"));

    options.setCaseSensitiveMatch(true);
    final String s129 = "class X {{ A a = new A(); }}";
    assertEquals("case sensitive match", 2, findMatchesCount(s129, "'Sym:A"));

    final String s133 = "class C { int a; int A() { a = 1; }} void c(int a) { a = 2; }";
    final String s134 = "a";
    assertEquals("find sym finds declaration", 4, findMatchesCount(s133, s134));

    final String s133_2 = "class C { int a() {} int A() { a(1); }}";
    assertEquals("find sym finds declaration", 2, findMatchesCount(s133_2, s134));

    final String source = "class A {" +
                          "  static A a() {};" +
                          "  void m() {" +
                          "    A a = A.a();" +
                          "  }" +
                          "}";
    assertEquals("No duplicate results", 4, findMatchesCount(source, "A"));
  }

  public void testSearchGenerics() {
    assertEquals("parameterized class match", 2, findMatchesCount(s81, "class '_<'T+> {}"));
    assertEquals("parameterized instanceof match", 1, findMatchesCount(s81, "'_Expr instanceof '_Type<'_Parameter+>"));
    assertEquals("parameterized cast match", 1, findMatchesCount(s81, "( '_Type<'_Parameter+> ) '_Expr"));
    assertEquals("parameterized symbol without variables matching", 2, findMatchesCount(s81, "S<T>"));
    assertEquals("parameterized definition match", 3, findMatchesCount(s81, "'_Type<'_Parameter+> 'a = '_Init?;"));
    assertEquals("parameterized method match", 1, findMatchesCount(s81, "class '_ { <'_+> '_Type 'Method('_* '_*); }"));
    assertEquals("parameterized constraint match", 2, findMatchesCount(s81_2, "class '_<'_+ extends 'res+> {}"));

    String s82_7 = "'Type";
    assertEquals("symbol matches parameterization", 29, findMatchesCount(s81,s82_7));
    assertEquals("symbol matches parameterization 2", 7, findMatchesCount(s81_2,s82_7));

    String s81_3 = " class A {\n" +
                   "  public static <T> Collection<T> unmodifiableCollection(int c) {\n" +
                   "    return new d<T>(c);\n" +
                   "  }\n" +
                   "  static class d<E> implements Collection<E>, Serializable {\n" +
                   "    public <T> T[] toArray(T[] a)       {return c.toArray(a);}\n" +
                   "  }\n" +
                   "}";
    assertEquals("typed symbol symbol", 2, findMatchesCount(s81_3, "class '_ { <'_+> '_Type 'Method('_* '_*); }"));

    String s81_4="class A<B> { \n" +
                 "  static <C> void c(D<E> f) throws R<S> {\n" +
                 "    if ( f instanceof G<H>) {\n" +
                 "      ((I<G<K>>)l).a();\n" +
                 "      throw new P<Q>();" +
                 "    }\n" +
                 "  }\n" +
                 "} " +
                 "class C {\n" +
                 "  void d(E f) throws Q {\n" +
                 "    if (g instanceof H) { a.c(); b.d(new A() {}); throw new Exception(((I)k)); }"+
                 "  }\n" +
                 "}";
    assertEquals("typed symbol", 8, findMatchesCount(s81_4, "'T<'_Subst+>"));

    String s81_5 = "class A { HashMap<String, Integer> variable = new HashMap<String, Integer>(\"aaa\");}";
    String s82_9 = "'_Type<'_GType, '_GType2> '_instance = new '_Type<'_GType, '_GType2>('_Param);";
    assertEquals("generic vars in new", 1, findMatchesCount(s81_5,s82_9));
    assertEquals("no exception on searching for diamond operator", 0, findMatchesCount(s81_5, "new 'Type<>('_Param)"));
    assertEquals("order of parameters matters", 0, findMatchesCount(s81_5, "HashMap<Integer, String>"));
    assertEquals("order of parameters matters 2", 2, findMatchesCount(s81_5, "HashMap<String, Integer>"));

    String source1 = "class Comparator<T> { private Comparator<String> c; private Comparator d; private Comparator e; }";
    assertEquals("qualified type should not match 1", 0, findMatchesCount(source1, "java.util.Comparator 'a;"));
    assertEquals("qualified type should not match 2", 0, findMatchesCount(source1, "java.util.Comparator<String> 'a;"));

    assertEquals("unparameterized type query should match", 3, findMatchesCount(source1, "Comparator 'a;"));
    assertEquals("parameterized type query should only match parameterized", 1,
                 findMatchesCount(source1, "Comparator<'_a> 'b;"));

    assertEquals("should find unparameterized only", 2, findMatchesCount(source1, "Comparator<'_a{0,0}> 'b;"));

    String source2 = "class A<@Q T> {}\n" +
                     "class B<T> {}";
    assertEquals("find annotated type parameter", 1, findMatchesCount(source2, "class '_A<@Q '_T> {}"));

    // @todo typed vars constrains (super),
    // @todo generic method invocation

    //String s83 = "class A {} List<A> a; List b;";
    //String s84 = "'a:List 'c;";
    //String s84_2 = "'a:List\\<'_\\> 'c;";
    //String s84_3 = "'a:List(?>\\<'_\\>) 'c;";
    //
    //assertEquals(
    //  "finding list",
    //  findMatchesCount(s83,s84),
    //  2
    //);
    //
    //assertEquals(
    //  "finding list 2",
    //  findMatchesCount(s83,s84_2),
    //  1
    //);
    //
    //assertEquals(
    //  "finding list 3",
    //  findMatchesCount(s83,s84_3),
    //  1
    //);
  }

  public void testSearchSubstitutions() {
    final String s15 = "'T;";

    assertEquals("search for parameterized pattern", 2, findMatchesCount(s14_1, s15));
    assertEquals("search for parameterized pattern 2", 5, findMatchesCount(s14_2, s15));

    options.setRecursiveSearch(false);

    assertEquals("search for parameterized pattern-non-recursive", 1, findMatchesCount(s14_1, s15));
    assertEquals("search for parameterized pattern 2-non-recursive", 2, findMatchesCount(s14_2, s15));

    final String s24_2  = "'T['_T2:.*i.* ]";
    // typed vars with arrays
    assertEquals("typed pattern with array 2-non-recursive", 4, findMatchesCount(s23, s24_2));

    options.setRecursiveSearch(true);

    assertEquals("search for parameterized pattern 3", 1, findMatchesCount(s14_2, "if('_T) { '_T2; }"));

    assertEquals("search for parameterized pattern in field selection", 1,
                 findMatchesCount(s17, "'_T1.'_T2.equals('_T3.'_T2);"));

    assertEquals("search for parameterized pattern with method call", 1,
                 findMatchesCount(s17, "'_T1.'_T2().equals('_T3.'_T2());"));

    assertEquals("search for parameterized pattern with method call ep.2", 4,
                 findMatchesCount(s17, "'_T1.'_T2"));

    assertEquals("search for same var constraint", 1, findMatchesCount(s19, "'_T1 'T2 = ('_T1)'_T3;"));

    assertEquals("search for same var constraint for semi anonymous typed vars", 1,
                 findMatchesCount(s19, "'_T1 '_T2 = ('_T1)'_T3;"));

    assertEquals("search for typed var constraint", 1, findMatchesCount(s22, "'_T1:Aa* 'T2 = ('_T1)'_T3;"));

    try {
      findMatchesCount(s22, "'_T1:A* 'T2 = ( '_T1:A+ )'_T3;");
      fail("search for noncompatible typed var constraint");
    } catch(MalformedPatternException ignored) {}

    assertEquals("search for same typed var constraint", 1, findMatchesCount(s22, "'_T1:Aa* 'T2 = ( '_T1 )'_T3;"));
    assertEquals("typed instanceof", 1, findMatchesCount(s65, " '_T instanceof '_T2:B"));

    try {
      findMatchesCount(s65, "'_T instanceof");
      fail("warn on incomplete instanceof");
    } catch (MalformedPatternException e) {
      assertEquals("Type expected", e.getMessage());
    }

    assertEquals("typed pattern with array", 2, findMatchesCount(s23, "'T['_T2:.*i.* ] = '_T3;"));
    assertEquals("typed pattern with array 2", 6, findMatchesCount(s23, s24_2));
    assertEquals("typed pattern in class name, method name, return type, parameter type and name", 1,
                 findMatchesCount(s25, "class 'T:.*Impl { '_T2 '_T3('_T4 '_T5) {\n\n} } "));
    assertEquals("finding interface", 1, findMatchesCount(s27, "interface 'T {}"));
    assertEquals("anonymous typed vars", 1, findMatchesCount(s29, "class '_ { void '_('_:int '_); } "));
    assertEquals("finding class descendants", 2, findMatchesCount(s31, "class '_ extends B {  } "));
    assertEquals("interface implementation", 2, findMatchesCount(s33, "class '_ implements B,C {  } "));
    assertEquals("different order of fields and methods", 1,
                 findMatchesCount(s35, "class '_ { double '_; int '_; int '_() {} void '_() {} } "));
    assertEquals("different order in throws", 1, findMatchesCount(s37, "class 'T { '_ '_() throws D,C {} } "));
    assertEquals("match of class without extends to class with it", 2, findMatchesCount(s39, "class 'T { } "));
    assertEquals("match of class without extends to class with it, ep. 2", 2,
                 findMatchesCount(s41, "class '_ { '_T '_T2 = '_T3; } "));
    assertEquals("match of class without extends to class with it, ep 3", 4,
                 findMatchesCount(s41, "class '_ { '_T '_T2; } "));
    assertEquals("match class with fields without initializers", 2,
                 findMatchesCount(s41, "class '_ { '_T '_T2 = '_T3{0,0}; } "));
    assertEquals("typed reference element", 2, findMatchesCount(s51, "class '_ extends '_ {  }"));
    assertEquals("empty name for typed var", 1, findMatchesCount(s59, "interface '_ { void '_(); }"));

    final String s64 = " class 'T { public void '_T2:run () {} }";
    assertEquals("comparing method with constructor", 1, findMatchesCount(s63,s64));
    assertEquals("finding nested class", 2, findMatchesCount(s63_2,s64));
    assertEquals("find nested class by special pattern", 1,
                 findMatchesCount(s63_2, "class '_ { class 'T { public void '_T2:run () {} } }"));

    assertEquals("* regexp for typed var", 5, findMatchesCount(s61, "{ 'T*; }"));
    assertEquals("+ regexp for typed var", 4, findMatchesCount(s61, "{ 'T+; }"));
    assertEquals("? regexp for typed var", 2, findMatchesCount(s61, "{ 'T?; }"));
    assertEquals("cast in method arguments", 1, findMatchesCount(s67, " (VirtualFile)'T"));
    assertEquals("searching for static field in static call", 2, findMatchesCount(s69, " System.out "));
    assertEquals("searching for static field in static call, 2", 2, findMatchesCount(s69, " java.lang.System.out "));
    assertEquals("* regexp for anonymous typed var", 3, findMatchesCount(s61, "{ '_*; }"));
    assertEquals("+ regexp for anonymous typed var", 2, findMatchesCount(s61, "{ '_+; }"));
    assertEquals("? regexp for anonymous typed var", 2, findMatchesCount(s61, "{ '_?; }"));
    assertEquals("statement inside anonymous class", 3, findMatchesCount(s71, " c(); "));
    assertEquals("clever regexp match", 2, findMatchesCount(s91, "'T:a"));
    assertEquals("clever regexp match 2", 2, findMatchesCount(s91, "'T:b"));
    assertEquals("clever regexp match 3", 2, findMatchesCount(s91, "'T:c"));
  }

  public void testSearchJavaDoc() {
    assertEquals("java doc comment in class in file", 1, findMatchesCount(s57, "/** @'T '_T2 */ class '_ { }"));

    final String s = "class A {" +
                     "  void m() {" +
                     "    /** tool */" +
                     "    class Local {}" +
                     "  }" +
                     "}";
    assertEquals("dangling javadoc followed by a local class", 1, findMatchesCount(s, "{\n/** tool */\nclass 'A {}\n}"));
    assertEquals("class with javadoc shouldn't find dangling javadoc and local class", 0, findMatchesCount(s, "/** tool */\nclass 'A {}"));

    assertEquals("javadoc comment for field", 2, findMatchesCount(s57, "class '_ { /** @serializable '_* */ '_ '_; }"));
    assertEquals("javadoc comment for method", 2, findMatchesCount(s57, "class '_ { /** @'T 1.4 */ '_ '_() {} }"));
    assertEquals("javadoc comment for method 2", 2, findMatchesCount(s57, "/** @'T 1.4 */ '_t '_m();"));
    assertEquals("just javadoc comment search", 4, findMatchesCount(s57, "/** @'T '_T2 */"));

    final String s84 = "    /**\n" +
                       "     * @hibernate.property\n" +
                       "     *  'Property+\n" +
                       "     */\n";
    assertEquals("XDoclet metadata", 2, findMatchesCount(s83,s84));

    final String s84_2 = "    /**\n" +
                         "     * @hibernate.property\n" +
                         "     *  update=\"fa.se\"\n" +
                         "     */\n";
    assertEquals("XDoclet metadata 2", 1, findMatchesCount(s83,s84_2));

    assertEquals("optional tag value match", 6, findMatchesCount(s57, "/** @'T '_T2? */"));
    assertEquals("multiple tags match +", 2, findMatchesCount(s75, " /** @'_tag+ '_value+ */"));
    assertEquals("multiple tags match *", 3, findMatchesCount(s75, " /** @'_tag* '_value* */"));
    assertEquals("multiple tags match ?", 3, findMatchesCount(s75, " /** @'_tag? '_value? */ class 't {}"));
    assertEquals("no infinite loop on javadoc matching", 1, findMatchesCount(s57, "/** 'Text */ class '_ { }"));

    final String source = "class outer {\n" +
                          "  /** bla */\n" +
                          "  class One {}\n" +
                          "  class Two {}\n" +
                          "}";
    final String pattern = "class '_A { /** '_Text */class 'B {}}";
    assertEquals("match inner class with javadoc", 1, findMatchesCount(source, pattern));
  }

  public void testNamedPatterns() {
    String s133 = "class String1 implements java.io.Serializable { " +
                  "private static final long serialVersionUID = -6849794470754667710L;" +
                  "private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];" +
                  "}" +
                  "class StringBuilder1 implements java.io.Serializable {" +
                  "    private void writeObject(java.io.ObjectOutputStream s)\n" +
                  "        throws java.io.IOException {\n" +
                  "        s.defaultWriteObject();\n" +
                  "    }" +
                  "private void readObject(java.io.ObjectInputStream s)\n" +
                  "        throws java.io.IOException, ClassNotFoundException {\n" +
                  "        s.defaultReadObject();\n" +
                  "    }" +
                  "    static final long serialVersionUID = 4383685877147921099L;" +
                  "}";
    String s134 = "class '_ implements '_:*Serializable {\n" +
                  "  static final long 'VersionField?:serialVersionUID = '_?;\n" +
                  "  private static final ObjectStreamField[] '_?:serialPersistentFields = '_?; \n" +
                  "  private void '_SerializationWriteHandler?:writeObject (ObjectOutputStream s) throws IOException;\n" +
                  "  private void '_SerializationReadHandler?:readObject (ObjectInputStream s) throws IOException, ClassNotFoundException;\n" +
                  "  Object '_SpecialSerializationReadHandler?:readResolve () throws ObjectStreamException;" +
                  "  Object '_SpecialSerializationWriteHandler?:writeReplace () throws ObjectStreamException;" +
                  "}";
    assertEquals("serialization match", 2, findMatchesCount(s133,s134));

    String s135 = "class SimpleStudentEventActionImpl extends Action { " +
                  "  public ActionForward execute(ActionMapping mapping,\n" +
                  "         ActionForm _form,\n" +
                  "         HttpServletRequest _request,\n" +
                  "         HttpServletResponse _response)" +
                  "  throws Exception {}" +
                  "} " +
                  "public class DoEnrollStudent extends SimpleStudentEventActionImpl { }" +
                  "public class DoCancelStudent extends SimpleStudentEventActionImpl { }";
    String s136 = "public class 'StrutsActionClass extends '_*:Action {" +
                  "  public ActionForward '_AnActionMethod:*execute (ActionMapping '_,\n" +
                  "                                 ActionForm '_,\n" +
                  "                                 HttpServletRequest '_,\n" +
                  "                                 HttpServletResponse '_);" +
                  "}";
    assertEquals("Struts actions", 2, findMatchesCount(s135, s136));

    final String s123 = "class NodeFilter {} public class MethodFilter extends NodeFilter {\n" +
                        "  private MethodFilter() {}\n" +
                        "\n" +
                        "  public static NodeFilter getInstance() {\n" +
                        "    if (instance==null) instance = new MethodFilter();\n" +
                        "    return instance;\n" +
                        "  }\n" +
                        "  private static NodeFilter instance;\n" +
                        "}";
    final String s124 = "class 'Class {\n" +
                        "  private 'Class('_* '_*) {\n" +
                        "   '_*;\n" +
                        "  }\n" +
                        "  private static '_Class2:* '_Instance;\n" +
                        "  static '_Class2 '_GetInstance() {\n" +
                        "    '_*;\n" +
                        "    return '_Instance;\n" +
                        "  }\n" +
                        "}";
    assertEquals("singleton search", 1, findMatchesCount(s123,s124));

    String s1111 = "class X {{" +
                   "if (true) { a=1; b=1; } else { a=1; }\n" +
                   "if(true) { a=1; } else { a=1; b=1; }\n" +
                   "if(true) { a=1; b=2; } else { a = 1; b=2; }" +
                   "}}";
    assertEquals("same multiple name pattern", 1, findMatchesCount(s1111, "if (true) { '_a{1,2}; } else { '_a; }"));
  }

  public void testHierarchy() {
    final String s105 = "class B {} class A extends B { }";
    assertEquals("extends match", 1, findMatchesCount(s105, "class '_ extends '_:[ref('T)] {}"));

    final String s107 = "interface IA {} interface IB extends IA { } interface IC extends IB {} interface ID extends IC {}" +
                        "class A implements IA {} class B extends A { } class C extends B implements IC {} class D extends C {}";
    assertEquals("extends navigation match", 2, findMatchesCount(s107, "class '_ extends 'Type:+A {}"));
    assertEquals("implements navigation match", 3, findMatchesCount(s107, "class '_ implements 'Type:+IA {}"));

    final String s109 = "interface I {} interface I2 extends I {} class A implements I2 {} class B extends A { } class C extends B {} class D { void e() { C c; B b; A a;} }";
    assertEquals("extends navigation match in definition", 3, findMatchesCount(s109, "'_:*A '_;"));
    assertEquals("implements navigation match in definition 2", 3, findMatchesCount(s109, "'_:*I '_;"));
    assertEquals("implements navigation match in definition 2 with nested conditions", 1,
                 findMatchesCount(s109, "'_:*[regex( I ) && ref('T)] '_;"));
    try {
      findMatchesCount(s109, "'_:*[regex( I ) && ref2('T)] '_;");
      fail("implements navigation match in definition 2 with nested conditions - incorrect cond");
    } catch(UnsupportedPatternException ignored) {}

    final String s111 = "interface E {} class A implements E {} class B extends A { int f = 0; } class C extends B {} class D { void e() { C c; B b; A a;} }";
    final String s112 = "'_";
    assertEquals("symbol match", 17, findMatchesCount(s111,s112));

    final String s115 = "class B {} public class C {}";
    assertEquals("public modifier for class", 1, findMatchesCount(s115, "public class '_ {}"));

    final String s117 = "class A { int b; void c() { int e; b=1; this.b=1; e=5; " +
                        "System.out.println(e); " +
                        "System.out.println(b); System.out.println(this.b);} }";
    assertEquals("fields of class", 4, findMatchesCount(s117, "this.'Field"));

    final String s119 = "class X {{ try { a.b(); } catch(IOException e) { c(); } catch(Exception ex) { d(); }}}";
    assertEquals("catches loose matching", 1, findMatchesCount(s119, "try { '_; } catch('_ '_) { '_; }"));
    assertEquals("catches loose matching 2", 0, findMatchesCount(s119, "try { '_; } catch(Throwable '_) { '_; }"));

    final String s121 = "class A { private int a; class Inner {} } " +
                        "class B extends A { private int a; class Inner2 {} }";
    assertEquals("hierarchical matching", 2, findMatchesCount(s121, "class '_ { int '_:* ; }"));
    assertEquals("hierarchical matching 2", 4, findMatchesCount(s121, "class '_ { int '_:+hashCode (); }"));
    assertEquals("hierarchical matching 3", 2, findMatchesCount(s121, "class '_ { class '_:* {} }"));
  }

  public void testSearchInCommentsAndLiterals() {
    String s1 = "class X {{" +
                "// This is some comment\n" +
                "/* This is another\n comment*/\n" +
                "// Some garbage\n"+
                "/** And now third comment*/\n" +
                "/** Some garbage*/ }}";
    assertEquals("Comment matching", 3, findMatchesCount(s1, "// 'Comment:[regex( .*(?:comment).* )]"));
    assertEquals("Comment matching, 2", 3, findMatchesCount(s1, "/* 'Comment:[regex( .*(?:comment).* )] */"));
    assertEquals("Java doc matching", 1, findMatchesCount(s1, "/** 'Comment:[regex( .*(?:comment).* )] */"));

    String s4 = "class X {{ java.util.Arrays.asList(\"'test\", \"another test\", \"garbage\"); }}";
    assertEquals("Literal content", 2, findMatchesCount(s4, "\"'test:[regex( .*test.* )]\""));
    assertEquals("Literal content with escaping", 1, findMatchesCount(s4, "\"''test\""));

    String s7 = "class X {{ String s = \"aaa\" }}";
    assertEquals("Simple literal content", 1, findMatchesCount(s7, "\"'test:[regex( aaa )]\""));

    String s9 = "class X {{ java.util.Arrays.asList(\" aaa \",\" bbb \",\" ccc ccc aaa\"); }}";
    assertEquals("Whole word literal content with alternations", 2,
                 findMatchesCount(s9, "\"'test:[regexw( aaa|ccc )]\""));
    assertEquals("Whole word literal content", 1, findMatchesCount(s9, "\"'test:[regexw( bbb )]\""));

    String s12 = "class X {{" +
                 "assert agentInfo != null : \"agentInfo is null\";\n" +
                 "assert addresses != null : \"addresses is null\";" +
                 "}}";
    assertEquals("reference to substitution in comment", 2,
                 findMatchesCount(s12, "assert '_exp != null : \"'_exp is null\";"));

    String s14 = "class X {{" +
                 "java.util.Arrays.asList(" +
                 "\"(some text with special chars)\"," +
                 "\" some\"," +
                 "\"(some)\"" +
                 ");" +
                 "}}";
    assertEquals("meta char in literal", 2, findMatchesCount(s14, "\"('a:[regexw( some )])\""));

    String s16 = "/**\n" +
                 "* Created by IntelliJ IDEA.\n" +
                 "* User: cdr\n" +
                 "* Date: Nov 15, 2005\n" +
                 "* Time: 4:23:29 PM\n" +
                 "* To change this template use File | Settings | File Templates.\n" +
                 "*/\n" +
                 "public class Y {\n" +
                 "}";
    String s17 = "/**\n" +
                 "* Created by IntelliJ IDEA.\n" +
                 "* User: '_USER\n" +
                 "* Date: '_DATE\n" +
                 "* Time: '_TIME\n" +
                 "* To change this template use File | Settings | File Templates.\n" +
                 "*/\n" +
                 "class 'c {\n" +
                 "}";
    assertEquals("complete comment match", 1, findMatchesCount(s16, s17));

    String s18 = "public class A {\n" +
                 "   private void f(int i) {\n" +
                 "       int g=0; //sss\n" +
                 "   }\n" +
                 "}";
    String s19 = "class '_c {\n" +
                 "   '_type '_f('_t '_p){\n" +
                 "       '_s; // sss\n" +
                 "   }\n" +
                 "}";
    assertEquals("statement match with comment", 1, findMatchesCount(s18,s19));
  }

  public void testOther() {
    assertEquals("optional init match in definition", 4, findMatchesCount(s73, " '_Type 'Var = '_Init?; "));
    assertEquals("null match", 0, findMatchesCount(s77, " class 'T:.*aaa {} "));
    assertEquals("body of method by block search", 2, findMatchesCount(s79, " { '_T 'T3 = '_T2?; '_*; } "));
    assertEquals("first matches, next not", 2, findMatchesCount(s95, " class '_ {private int 'T+:field.* ;}"));

    final String s97 = "class A { int c; void b() { C d; } } class C { C() { A a; a.b(); a.c=1; } }";
    assertEquals("method predicate match", 1, findMatchesCount(s97, "'_.'_:[ref('T)] ()"));
    assertEquals("field predicate match", 1, findMatchesCount(s97, "'_.'_:[ref('T)]"));
    assertEquals("dcl predicate match", 1, findMatchesCount(s97, "'_:[ref('T)].'_ ();"));

    final String s99 = "class X {{ char s = '\\u1111';  char s1 = '\\n'; }}";
    assertEquals("char constants in pattern", 1, findMatchesCount(s99, " char 'var = '\\u1111'; "));
    assertEquals("char constants in pattern 2", 1, findMatchesCount(s99, " char 'var = '\\n'; "));

    assertEquals("class predicate match (from definition)", 3, findMatchesCount(s97, "'_:[ref('T)] '_;"));

    final String s101 = "class A { void b() { String d; String e; String[] f; f.length=1; f.length=1; } }";
    assertEquals("distinct match", 1, findMatchesCount(s101, "'_:[ref('T)] '_;"));

    String s107 = "class A {\n" +
                  "  /* */\n" +
                  "  void a() {\n" +
                  "  }" +
                  "  /* */\n" +
                  "  int b = 1;\n" +
                  "  /*" +
                  "   *" +
                  "   */\n" +
                  "   class C {}" +
                  "}";
    String s108 = "  /*" +
                  "   *" +
                  "   */";
    assertEquals("finding comments without typed var", 1, findMatchesCount(s107,s108));

    String s109 = "class X {{" +
                  "class A { void b(); int b(int c); char d(char e); }\n" +
                  "A a; a.b(1); a.b(2); a.b(); a.d('e'); a.d('f'); a.d('g');" +
                  "}}";
    assertEquals("caring about method return type", 2, findMatchesCount(s109, "'_a.'_b:[exprtype( int ) ]('_c*);"));

    String s111 = "class A { void getManager() { getManager(); } };\n" +
                  "class B { void getManager() { getManager(); getManager(); } };";
    assertEquals("caring about missing qualifier type", 2, findMatchesCount(s111, "'Instance?:[exprtype( B )].getManager()"));
    assertEquals("static query should not match instance method", 0, findMatchesCount(s111, "'Instance?:[regex( B )].getManager()"));
    assertEquals("static query should not match instance method 2", 0, findMatchesCount(s111, "B.getManager()"));

    String s113 = "class A { static void a() { a(); }}\n" +
                  "class B { static void a() { a(); a(); }}\n";
    assertEquals("should care about implicit class qualifier", 2, findMatchesCount(s113, "'_Q?:[regex( B )].a()"));
    assertEquals("should match simple implicit class qualifier query", 2, findMatchesCount(s113, "B.a()"));
    assertEquals("instance query should not match static method", 0, findMatchesCount(s113, "'_Q?:[exprtype( B )].a()"));

    String s115 = "class A { int a; int f() { return a; }}\n" +
                  "class B { int a; int g() { return a + a; }}\n";
    assertEquals("should care about implicit instance qualifier", 2, findMatchesCount(s115, "'_Instance?:[exprtype( B )].a"));
    assertEquals("should not match instance method", 0, findMatchesCount(s115, "A.a"));

    String s117 = "class A { static int a; static int f() { return a; }}\n" +
                  "class B { static int a; static int g() { return a + a; }}\n";
    assertEquals("should care about implicit class qualifier for field", 2, findMatchesCount(s117, "'_Q?:[regex( B )].a"));

    // b) hierarchy navigation support
    // c) or search support

    // e) xml search (down-up, nested query), navigation from xml representation <-> java code
    // f) impl data conversion (jdk 1.5 style) <-> other from (replace support)

    // Directions:
    // @todo different navigation on sub/supertyping relation (fixed depth), methods implementing interface,
    // g.  like predicates
    // i. performance
    // more context for top level classes, difference with interface, etc

    // global issues:
    // @todo matches out of context
    // @todo proper regexp support

    // @todo define strict equality of the matches
    // @todo search for field selection retrieves packages also
  }

  public void testFQNInPatternAndVariableConstraints() {
    String s1 = "import java.awt.List;\n" +
                "class A { List l; }";
    String s2 = "class '_ { 'Type:java\\.util\\.List '_Field; }";
    assertEquals("No matches for qualified class", 0, findMatchesCount(s1, s2));

    String s1_2 = "import java.util.List;\n" +
                  "class A { List l; }";
    assertEquals("Matches for qualified class", 1, findMatchesCount(s1_2, s2));

    String s3 = "import java.util.ArrayList;\n" +
                "class A { ArrayList l; }";
    assertEquals("Matches for qualified class in hierarchy", 1,
                 findMatchesCount(s3, "class '_ { 'Type:*java\\.util\\.Collection '_Field; }"));

    String s5 = "import java.util.List;\n" +
                "class A { { List l = new List(); l.add(\"1\"); }  }";
    assertEquals("Matches for qualified expr type in hierarchy", 2,
                 findMatchesCount(s5, "'a:[exprtype( *java\\.util\\.Collection )]"));

    String s6 = "'a:[exprtype( java\\.util\\.List )]";
    assertEquals("Matches for qualified expr type", 2, findMatchesCount(s5, s6));

    String s5_2 = "import java.awt.List;\n" +
                  "class A { { List l = new List(); l.add(\"1\"); } }";
    assertEquals("No matches for qualified expr type", 0, findMatchesCount(s5_2, s6));

    String s6_3 = "java.util.List '_a = '_b?;";
    assertEquals("Matches for qualified var type in pattern", 1, findMatchesCount(s5, s6_3));
    assertEquals("No matches for qualified var type in pattern", 0, findMatchesCount(s5_2, s6_3));

    String s7 = "import java.util.List;\n" +
                "class A extends List { }";

    String s8 = "class 'a extends java.util.List {}";
    assertEquals("Matches for qualified type in pattern", 1, findMatchesCount(s7, s8));

    String s7_2 = "import java.awt.List;\n" +
                  "class A extends List {}";
    assertEquals("No matches for qualified type in pattern", 0, findMatchesCount(s7_2, s8));

    String s9 = "class X {{" +
                "  String.intern(\"1\");" +
                "  java.util.Collections.sort(null);" +
                "  java.util.Collections.sort(null);" +
                "}}";
    assertEquals("FQN in class name",1,
                 findMatchesCount(s9, "java.lang.String.'_method ( '_params* )"));
  }

  public void testAnnotations() {
    String s1 = "@MyBean(\"\")\n" +
                "@MyBean2(\"\")\n" +
                "public class TestBean {}\n" +
                "@MyBean2(\"\")\n" +
                "@MyBean(value=\"\")\n" +
                "public class TestBean2 {}\n" +
                "public class TestBean3 {}\n" +
                "@MyBean(\"a\")\n" +
                "@MyBean2(\"a\")\n" +
                "public class TestBean4";
    String s2 = "@MyBean(\"\")\n" +
                "@MyBean2(\"\")\n" +
                "public class '_a {}\n";

    assertEquals("Simple find annotated class", 2, findMatchesCount(s1, s2));
    assertEquals("Match value of anonymous name value pair 1", 1, findMatchesCount(s1, "@MyBean(\"a\") class '_a {}"));
    assertEquals("Match value of anonymous name value pair 2", 2, findMatchesCount(s1, "@MyBean(\"\") class '_a {}"));

    String s3 = "@VisualBean(\"????????? ?????????? ? ??\")\n" +
                "public class TestBean\n" +
                "{\n" +
                "    @VisualBeanField(\n" +
                "            name = \"??? ????????????\",\n" +
                "            initialValue = \"?????????????\"\n" +
                "            )\n" +
                "    public String user;\n" +
                "\n" +
                "    @VisualBeanField(\n" +
                "            name = \"??????\",\n" +
                "            initialValue = \"\",\n" +
                "            fieldType = FieldTypeEnum.PASSWORD_FIELD\n" +
                "            )\n" +
                "    public String password;\n" +
                "\n" +
                "    @VisualBeanField(\n" +
                "            initialValue = \"User\",\n" +
                "            name = \"????? ???????\",\n" +
                "            name = \"Second name\",\n" +
                "            fieldType = FieldTypeEnum.COMBOBOX_FIELD,\n" +
                "            comboValues = {\n" +
                "               @ComboFieldValue(\"Administrator\"),\n" +
                "               @ComboFieldValue(\"User\"),\n" +
                "               @ComboFieldValue(\"Guest\")}\n" +
                "            )    \n" +
                "    public String accessRights;\n" +
                "    \n" +
                "    public String otherField;\n" +
                "}";
    String s4 = "class '_a {\n" +
                "  @'_Annotation+ ( 'AnnotationMember*:name = '_AnnotationValue* )\n" +
                "  String '_field* ;\n" +
                "}";
    assertEquals("Find annotation members of annotated field class", 4, findMatchesCount(s3, s4));

    String s4_2 = "class '_a {\n" +
                  "  @'_Annotation+ ()\n" +
                  "  String 'field* ;\n" +
                  "}";
    assertEquals("Find annotation fields", 3, findMatchesCount(s3, s4_2));

    String s5 = "class A {" +
                "  @NotNull private static Collection<PsiElement> resolveElements(final PsiReference reference, final Project project) {}\n" +
                "  @NotNull private static Collection resolveElements2(final PsiReference reference, final Project project) {}\n" +
                "}";

    assertEquals("Find annotated methods", 2,
                 findMatchesCount(s5, "class '_c {@NotNull '_rt 'method* ('_pt* '_p*){ '_inst*; } }"));
    assertEquals("Find annotated methods, 2", 2,
                 findMatchesCount(s5, "class '_c {@'_:NotNull '_rt 'method* ('_pt* '_p*){ '_inst*; } }"));

    String s7 = "class A { void message(@NonNls String msg); }\n" +
                "class B { void message2(String msg); }\n" +
                "class C { void message2(String msg); }";
    assertEquals("Find not annotated methods", 2, findMatchesCount(s7, "class '_A { void 'b( @'_Ann{0,0}:NonNls String  '_); }"));

    String s9 = "class A {\n" +
                "  Object[] method1() {}\n" +
                "  Object method1_2() {}\n" +
                "  Object method1_3() {}\n" +
                "  Object method1_4() {}\n" +
                "  @MyAnnotation Object[] method2(int a) {}\n" +
                "  @NonNls Object[] method3() {}\n" +
                "}";
    assertEquals("Find not annotated methods, 2", 2,
                 findMatchesCount(s9, "class '_A { @'_Ann{0,0}:NonNls '_Type:Object\\[\\] 'b+( '_pt* '_p* ); }"));
    assertEquals("Find not annotated methods, 2", 2,
                 findMatchesCount(s9, "class '_A { @'_Ann{0,0}:NonNls '_Type [] 'b+( '_pt* '_p* ); }"));
    assertEquals("Find not annotated methods, 2", 2,
                 findMatchesCount(s9, "class '_A { @'_Ann{0,0}:NonNls '_Type:Object [] 'b+( '_pt* '_p* ); }"));

    String s11 = "class A {\n" +
                 "  @Foo(value=baz) int a;\n" +
                 "  @Foo(value=baz2) int a2;\n" +
                 "  @Foo(baz2) int a3;\n" +
                 "  @Foo(value2=baz2) int a4;\n" +
                 "  @Foo(value2=baz2) int a5;\n" +
                 "  @Foo(value2=baz3) int a6;\n" +
                 "  @Foo(value2=baz3) int a7;\n" +
                 "  @Foo(value2=baz3) int a8;\n" +
                 "  @Foo(value2=baz4) int a9;\n" +
                 "  @Foo int a10;\n" +
                 "}";
    assertEquals("Find anno parameter value", 1, findMatchesCount(s11, "@Foo(value=baz) int 'a;"));
    assertEquals("Find anno parameter value", 2, findMatchesCount(s11, "@Foo(value='baz:baz2 ) int '_a;"));
    assertEquals("Find anno parameter value", 3, findMatchesCount(s11, "@Foo('value:value2 = baz3 ) int '_a;"));
    assertEquals("Find anno parameter value", 3, findMatchesCount(s11, "@Foo('value:value2 = '_baz3:baz3 ) int '_a;"));
    assertEquals("Find anno parameter value", 0, findMatchesCount(s11, "@Foo('value:value2 = '_baz3:baz ) int '_a;"));
    assertEquals("Find anno parameter value", 6, findMatchesCount(s11, "@Foo('value:value2 = '_baz3 ) int '_a;"));
    try {
      findMatchesCount(s11, "@Foo('value:value2 = ) int '_a;");
      fail("should report missing value");
    } catch (MalformedPatternException ignored) {}
    assertEquals("Match anno parameter name", 6, findMatchesCount(s11, "@Foo(value2='_value)"));
    assertEquals("Match anno parameter name 2", 3, findMatchesCount(s11, "@Foo(value='_value)"));
    assertEquals("Match anno parameter value only", 4, findMatchesCount(s11, "@Foo(baz2)"));
    assertEquals("Match all anno parameters", 9, findMatchesCount(s11, "@Foo('_any)"));
    assertEquals("Match all annotations", 10, findMatchesCount(s11, "@Foo('_any*)"));
    // todo
    //assertEquals("Match annotations without parameters", 1, findMatchesCount(s11, "@Foo('_any{0,0})"));

    String source1 = "class A {" +
                     "  void m() {" +
                     "    new @B Object();" +
                     "  }" +
                     "}";
    assertEquals("Find annotated new expression", 1, findMatchesCount(source1, "new Object()"));
    assertEquals("Find annotated new expression", 1, findMatchesCount(source1, "new @B Object()"));
    assertEquals("Find annotated new expression", 0, findMatchesCount(source1, "new @C Object()"));

    String source2 = "@X\n" +
                     "class A {\n" +
                     "  @Y int value;" +
                     "  @Y int void m(@Z int i) {\n" +
                     "    return 1;\n" +
                     "  }\n" +
                     "}\n";
    assertEquals("Find all annotations", 4, findMatchesCount(source2, "@'_Annotation"));

    String source3 = "class A<@HH T> extends @HH Object {\n" +
                     "  @HH final String s = (@HH String) new @HH Object();\n" +
                     "  final String t = (String) new Object();\n" +
                     "  Map<@HH String, @HH List<@HH String>> map;\n" +
                     "}\n";
    assertEquals("Find annotated casts", 1, findMatchesCount(source3, "(@'_A 'Cast) '_Expression"));
    assertEquals("Find annotated new expressions", 1, findMatchesCount(source3, "new @'_A 'Type()"));
    assertEquals("Find all annotations 2", 8, findMatchesCount(source3, "@'_Annotation"));

    // package-info.java
    final String source4 = "/**\n" +
                           " * documentation\n" +
                           " */\n" +
                           "@Deprecated\n" +
                           "package one.two;";
    assertEquals("Find annotation on package statement", 1, findMatchesCount(source4, "@'_Annotation"));

    final String source5 ="class A {" +
                          "  boolean a(Object o) {" +
                          "    return o instanceof @HH String;" +
                          "  }" +
                          "}";
    assertEquals("Find annotation on instanceof expression", 1, findMatchesCount(source5, "'_a instanceof @HH String"));
    assertEquals("Match annotation correctly on instanceof expression", 0, findMatchesCount(source5, "'_a instanceof @GG String"));

    String source6 = "@SuppressWarnings({\"WeakerAccess\", \"unused\", \"UnnecessaryInterfaceModifier\"}) class A {" +
                     "  @SuppressWarnings({\"unused\"}) @NotNull int i;" +
                     "}";
    assertEquals("Find SuppressWarnings annotations", 2, findMatchesCount(source6, "@SuppressWarnings"));
    assertEquals("Find SuppressWarnings annotations", 2, findMatchesCount(source6, "@SuppressWarnings(value='_any)"));
    assertEquals("Find annotation with 3 value array initializer", 1, findMatchesCount(source6, "@SuppressWarnings({'_value{3,3} })"));

  }

  public void testBoxingAndUnboxing() {
    String s1 = "class X {{" +
                "class A { void b(Integer i); void b2(int i); void c(int d); void c2(Integer d); }\n" +
                "A a;\n" +
                "a.b2(1)\n;" +
                "a.b2(1)\n;" +
                "a.b(1)\n;" +
                "a.b( new Integer(0) )\n;" +
                "a.b( new Integer(0) )\n;" +
                "a.c(new Integer(2));\n" +
                "a.c(new Integer(3));\n" +
                "a.c2(new Integer(3));\n" +
                "a.c(3);\n" +
                "Integer i = 4;\n" +
                "int j = Integer.valueOf(4);\n" +
                "}}";

    assertEquals("Find boxing in method call", 1, findMatchesCount(s1, "a.'b('_Params:[formal( Integer ) && exprtype( int ) ])"));
    assertEquals("Find unboxing in method call", 2, findMatchesCount(s1, "a.c('_Params:[formal( int ) && exprtype( Integer ) ])"));
    assertEquals("Find any boxing", 2, findMatchesCount(s1, "'_a:[formal( Integer ) && exprtype( int ) ]"));
    assertEquals("Find any unboxing", 3, findMatchesCount(s1, "'_a:[formal( int ) && exprtype( Integer ) ]"));
  }

  public void testCommentsInDclSearch() {
    String s1 = "class A {\n" +
                "  int a; // comment\n" +
                "  char b;\n" +
                "  int c; // comment2\n" +
                "}";
    assertEquals("Find field by dcl with comment",2,findMatchesCount(s1, "'_Type '_Variable = '_Value?; //'Comment"));

    String s1_2 = "class A {\n" +
                  "  // comment\n" +
                  "  int a;\n" +
                  "  char b;\n" +
                  "  // comment2\n" +
                  "  int c;\n" +
                  "}";
    String s2_2 = "//'Comment\n" +
                  "'_Type '_Variable = '_Value?;";
    assertEquals("Find field by dcl with comment 2",2,findMatchesCount(s1_2,s2_2));
  }

  public void testSearchingEmptyModifiers() {
    String s1 = "class A {\n" +
                "  int a;\n" +
                "  private char b;\n" +
                "  private char b2;\n" +
                "  public int c;\n" +
                "  public int c2;\n" +
                "}";
    assertEquals("Finding package-private dcls",1,
                 findMatchesCount(s1, "@Modifier(\"packageLocal\") '_Type '_Variable = '_Value?;"));
    assertEquals("Finding package-private dcls",3,
                 findMatchesCount(s1, "@Modifier({\"packageLocal\",\"private\"}) '_Type '_Variable = '_Value?;"));
    try {
      findMatchesCount(s1, "@Modifier({\"PackageLocal\",\"private\"}) '_Type '_Variable = '_Value?;");
      fail("Finding package-private dcls");
    } catch(MalformedPatternException ignored) {}

    String s3 = "class A {\n" +
                "  int a;\n" +
                "  static char b;\n" +
                "  static char b2;\n" +
                "}";
    assertEquals("Finding instance fields",1,
                 findMatchesCount(s3, "@Modifier(\"Instance\") '_Type '_Variable = '_Value?;"));
    assertEquals("Finding all fields",3,
                 findMatchesCount(s3, "@Modifier({\"static\",\"Instance\"}) '_Type '_Variable = '_Value?;"));

    String s5 = "class A {}\n" +
                "abstract class B {}\n" +
                "final class C {}\n" +
                "class D {}";
    assertEquals("Finding instance classes",3,findMatchesCount(s5, "@Modifier(\"Instance\") class 'Type {}"));
    assertEquals("Finding all classes",4,
                 findMatchesCount(s5, "@Modifier({\"abstract\",\"final\",\"Instance\"}) class 'Type {}"));
  }

  public void testSearchTransientFieldsWithModifier() {
    String source =
      "public class TestClass {\n" +
      "  transient private String field1;\n" +
      "  transient String field2;\n" +
      "  String field3;\n" +
      "}";
    assertEquals("Finding package-private transient fields", 1,
                 findMatchesCount(source, "transient @Modifier(\"packageLocal\") '_Type '_Variable = '_Value?;"));
  }

  public void test() {
    String s1 = "class X {{" +
                "if (LOG.isDebugEnabled()) {\n" +
                "  int a = 1;\n" +
                "  int a = 1;\n" +
                "}" +
                "}}";
    String pattern = "if ('_Log.isDebugEnabled()) {\n" +
                     "  '_ThenStatement;\n" +
                     "  '_ThenStatement;\n" +
                     "}";
    assertEquals("Comparing declarations", 1, findMatchesCount(s1, pattern));
  }

  public void testFindStaticMethodsWithinHierarchy() {
    String s1 = "class X {{" +
                "class A {}\n" +
                "class B extends A { static void foo(); }\n" +
                "class B2 extends A { static void foo(int a); }\n" +
                "class B3 extends A { static void foo(int a, int b); }\n" +
                "class C { static void foo(); }\n" +
                "B.foo();\n" +
                "B2.foo(1);\n" +
                "B3.foo(2,3);\n" +
                "C.foo();" +
                "}}";
    assertEquals("Find static methods within expr type hierarchy", 3,
                 findMatchesCount(s1, "'_Instance:[regex( *A )].'_Method:[regex( foo )] ( '_Params* )"));
  }

  public void testFindClassesWithinHierarchy() {
    String s1 = "class A implements I {}\n" +
                "interface I {}\n" +
                "class B extends A implements I { }\n" +
                "class B2 implements I  { }\n" +
                "class B3 extends A { }\n" +
                "class C extends B2 { static void foo(); }\n";
    assertEquals("Find class within type hierarchy with not", 1,
                 findMatchesCount(s1, "class '_ extends '_Extends:[!regex( *A )] implements '_Implements:[regex( I )] {}"));
    assertEquals("Find class within type hierarchy with not, 2", 1,
                 findMatchesCount(s1, "class '_ extends '_Extends:[!regex( *A )]{}"));
    assertEquals("Search in hierarchy on class identifier", 2, findMatchesCount(s1, "class '_X:*B2 {}"));
  }

  public void testFindTryWithoutProperFinally() {
    String s1 = "class X {{" +
                "try {\n" +
                "  conn = 1;\n" +
                "} finally {\n" +
                "  conn.close();\n" +
                "}\n" +
                "try {\n" +
                "  conn = 1;\n" +
                "} finally {\n" +
                "  int a = 1;\n" +
                "}\n" +
                "try {\n" +
                "  conn = 1;\n" +
                "} finally {\n" +
                "  int a = 1;\n" +
                "}" +
                "}}";
    String s2 = "try { '_StatementBefore*; '_Dcl:[regex( conn = 1 )]; '_StatementAfter*; } finally { '_Finally*:[!regex( .*conn.* ) ]; }";
    assertEquals("FindTryWithoutProperFinally", 2, findMatchesCount(s1,s2));
  }

  public void testBug() {
    String s1 = "public class DiallingNumber extends DataGroup\n" + "{\n" + "    protected static byte [] CLEAR = { };\n" + "\n" +
                "    private static DataItemTemplate template;\n" + "\n" + "\tprotected DataTemplate createDefaultTemplate()\n" + "\t{\n" +
                "        return null;\n" + "    }\n" + "}";
    String s2 = "class '_Class {\n" + "    static '_FieldType '_FieldName:.*template.* = '_FieldInitial?;\n" +
                "    '_RetType createDefaultTemplate() { '_Statements*; }\n" + "\t'_Content*\n" + "}";
    assertEquals("Bug in class matching", 1, findMatchesCount(s1,s2));
  }

  //public void testFindFieldUsageByQName() {
  //  String s1 = "{ class A { int b; { b = 1; } } class B extends A { { this.b = 2} } { B i; i.b = 3; } }";
  //  String s2 = "A.b";
  //  assertEquals( 3, findMatchesCount(s1,s2));
  //}
  //
  //public void testFindMethodUsageByQName() {
  //  String s1 = "{ class A { void b(int a) {} { b(1); } } class B extends A { { this.b(2); } } { B i; i.b(3); } }";
  //  String s2 = "A.b";
  //  assertEquals( 3, findMatchesCount(s1,s2));
  //}

  public void testStaticInstanceInitializers() {
    String s1 = "public class DiallingNumber {" +
                "  static { int a = 1; }" +
                "  static { int b = 1; }" +
                "  { int c = 2; }" +
                "}";
    assertEquals("Static / instance initializers", 2, findMatchesCount(s1, "static { 't*; }"));
    assertEquals("Static / instance initializers", 1, findMatchesCount(s1, "@Modifier(\"Instance\") { 't*; }"));
    assertEquals("Static / instance initializers", 3, findMatchesCount(s1, "{ 't*; }"));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/java/";
  }

  public void testDoNotFindReturn() throws IOException {
    String s1 = loadFile(getTestName(false) + ".java");
    String s2 = "ApplicationManager.getApplication().runReadAction(new Runnable() {\n" +
                "      public void run() {\n" +
                "        't*:[ !regex( .*return.* ) ];\n" +
                "    }});";
    assertEquals(0, findMatchesCount(s1,s2));
  }

  public void testDownUpMatch() {
    String s1 = "class A {\n" +
                "  int bbb(int c, int ddd, int eee) {\n" +
                "    int a = 1;\n" +
                "    try { int b = 1; } catch(Type t) { a = 2; } catch(Type2 t2) { a = 3; }\n" +
                "  }\n" +
                "}";

    final List<PsiVariable> vars = new ArrayList<>();
    @SuppressWarnings("deprecation") final PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("_.java", s1);

    //noinspection AnonymousInnerClassMayBeStatic
    file.acceptChildren(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitVariable(final PsiVariable variable) {
        super.visitVariable(variable);
        vars.add(variable);
      }
    });

    assertEquals(7, vars.size());

    Matcher testMatcher = new Matcher(getProject());
    MatchOptions options = new MatchOptions();
    options.fillSearchCriteria("try  { '_st*; } catch('_Type 't+) { '_st2*; }");
    options.setFileType(StdFileTypes.JAVA);

    List<MatchResult> results = new ArrayList<>();
    for(PsiVariable var:vars) {
      final List<MatchResult> matchResult = testMatcher.matchByDownUp(var, options);
      results.addAll(matchResult);
      assertTrue((var instanceof PsiParameter && var.getParent() instanceof PsiCatchSection && !matchResult.isEmpty()) ||
                 matchResult.isEmpty());
    }

    assertEquals(2, results.size());
    MatchResult result = results.get(0);
    assertEquals("t", result.getMatchImage());

    result = results.get(1);
    assertEquals("t2", result.getMatchImage());

    results.clear();

    options.clearVariableConstraints();
    options.fillSearchCriteria("try  { '_st*; } catch('Type:Type2 '_t) { '_st2*; }");

    for(PsiVariable var:vars) {
      final PsiTypeElement typeElement = var.getTypeElement();
      final List<MatchResult> matchResult = testMatcher.matchByDownUp(typeElement, options);
      results.addAll(matchResult);
      assertTrue((var instanceof PsiParameter && var.getParent() instanceof PsiCatchSection && !matchResult.isEmpty()) ||
                 matchResult.isEmpty());
    }

    assertEquals(1, results.size());

    result = results.get(0);
    assertEquals("Type2", result.getMatchImage());
  }

  @SuppressWarnings("unused")
  public void _testContainsPredicate() {
    String s1 = "{{\n" +
                "  int a;\n" +
                "  a = 1;\n" +
                "}\n" +
                "{\n" +
                "  int b = 1;\n" +
                "  b = 1;\n" +
                "}\n" +
                "{\n" +
                "  int c = 2;\n" +
                "  c = 2;\n" +
                "}}";
    assertEquals(2, findMatchesCount(s1, "{ '_a*:[contains( \"'type '_a = '_b;\" )]; }"));
    assertEquals(1, findMatchesCount(s1, "{ '_a*:[!contains( \"'_type '_a = '_b;\" )]; }"));
  }

  public void testWithinPredicate() {
    String s1 = "class X {{" +
                "if (true) {\n" +
                "  int a = 1;\n" +
                "}\n" +
                "if (true) {\n" +
                "  int b = 1;\n" +
                "}\n" +
                "while(true) {\n" +
                "  int c = 2;\n" +
                "}" +
                "}}";
    String s2 = "[within( \"if ('_a) { '_st*; }\" )]'_type 'a = '_b;";
    assertEquals(2,findMatchesCount(s1, s2));

    String s2_2 = "[!within( \"if ('_a) { '_st*; }\" )]'_type 'a = '_b;";
    assertEquals(1,findMatchesCount(s1, s2_2));

    String s3 = "class X {{" +
                "if (true) {\n" +
                "  if (true) return;\n" +
                "  int a = 1;\n" +
                "}\n" +
                "else if (true) {\n" +
                "  int b = 2;\n" +
                "  return;\n" +
                "}\n" +
                "int c = 3;\n" +
                "}}";
    assertEquals(2,findMatchesCount(s3, s2));
    assertEquals(1,findMatchesCount(s3, s2_2));
  }

  public void testWithinPredicate2() {
    String s3 = "class C {\n" +
                "  void aaa() {\n" +
                "        LOG.debug(1);\n" +
                "        LOG.debug(2);\n" +
                "        LOG.debug(3);\n" +
                "        LOG.debug(4);\n" +
                "        LOG.debug(5);\n" +
                "        if (true) {\n" +
                "            LOG.debug(6);\n" +
                "        }\n" +
                "        if (true) LOG.debug(7);\n" +
                "        if (true) { int 1 = 1; } else { LOG.debug(8); }\n" +
                "        if (true) {\n" +
                "          if (true) {}\n" +
                "          if (true) {}\n" +
                "        } else{\n" +
                "          LOG.debug(9);\n" +
                "        }" +
                "    }" +
                "}";
    String s4 = "[!within( \"if('_a) { 'st*; }\" )]LOG.debug('_params*);";

    assertEquals(7,findMatchesCount(s3, s4));
  }

  public void testMultiStatementPatternWithTypedVariable() {
    String s = "class X {{ Integer i; i.valueOf(); }}";
    assertEquals(1, findMatchesCount(s, "Integer '_i;\n'_i.valueOf();"));

    String s_2 = "class X {{ Integer i; int a = 1; i.valueOf(); }}";
    assertEquals(1, findMatchesCount(s_2, "Integer '_i;\n'_st; '_i.valueOf();"));

    String pattern = "Integer '_i;\n'_st*; '_i.valueOf();";
    assertEquals(1, findMatchesCount(s_2, pattern));
    assertEquals(1, findMatchesCount(s, pattern));
  }

  public void testFindAnnotationDeclarations() {
    String s = "interface Foo {} interface Bar {} @interface X {}";
    String s2 = "@interface 'x {}";

    assertEquals(1, findMatchesCount(s,s2));
  }

  public void testFindEnums() {
    String s = "class Foo {} class Bar {} enum X {}";
    String s2 = "enum 'x {}";

    assertEquals(1, findMatchesCount(s,s2));
  }

  public void testFindDeclaration() {
    String s = "public class F {\n" +
               "  static Category cat = Category.getInstance(F.class.getName());\n" +
               "  Category cat2 = Category.getInstance(F.class.getName());\n" +
               "  Category cat3 = Category.getInstance(F.class.getName());\n" +
               "}";
    String s2 = "static '_Category '_cat = '_Category.getInstance('_Arg);";

    assertEquals(1, findMatchesCount(s,s2));
  }

  public void testFindMethodCallWithTwoOrThreeParameters() {
    String source = "class X {{ String.format(\"\"); String.format(\"\", 1); String.format(\"\", 1, 2); String.format(\"\", 1, 2, 3); }}";
    String pattern = "'_Instance.'_MethodCall('_Parameter{2,3})";

    assertEquals(2, findMatchesCount(source, pattern));
  }

  public void testFindMethodWithCountedExceptionsInThrows() {
    String source = "class A {" +
                    "  void a() {}" +
                    "  void b() throws E1 {}" +
                    "  void c() throws E1, E2{}" +
                    "  void d() throws E1, E2, E3 {}" +
                    "}";

    String pattern1 = "class '_A {" +
                      "  '_type+ 'method+ () throws '_E{0,0};" +
                      "}";
    assertEquals(1, findMatchesCount(source, pattern1));

    String pattern2 = "class '_A {" +
                      "  '_type+ 'method+ () throws '_E{1,2};" +
                      "}";
    assertEquals(2, findMatchesCount(source, pattern2));

    String pattern3 = "class '_A {" +
                      "  '_type+ 'method+ () throws '_E{2,2};" +
                      "}";
    assertEquals(1, findMatchesCount(source, pattern3));

    String pattern4 = "class '_A {" +
                      "  '_type+ 'method+ () throws '_E{0,0}:[ regex( E2 )];" +
                      "}";
    assertEquals(2, findMatchesCount(source, pattern4));
  }

  public void testFindMethodsCalledWithinClass() {
    String source = "class A {" +
                    "  void a() {}" +
                    "  static void b() {}" +
                    "  void c() {" +
                    "    a();" +
                    "    b();" +
                    "  }" +
                    "}" +
                    "class B extends A {" +
                    "  void d() {" +
                    "    a();" +
                    "    b();" +
                    "  }" +
                    "}";
    String pattern1 = "this.a()";
    assertEquals(2, findMatchesCount(source, pattern1));
  }

  public void testFindReferenceWithParentheses() {
    String source = "class A {" +
                    "  String value;" +
                    "  A(String v) {" +
                    "    value = (value);" +
                    "    System.out.println(((2)));" +
                    "    System.out.println(2);" +
                    "  }" +
                    "}";

    String pattern1 = "'_value='_value";
    assertEquals(1, findMatchesCount(source, pattern1));

    String pattern2 = "System.out.println('_v);" +
                      "System.out.println('_v);";
    assertEquals(1, findMatchesCount(source, pattern2));
  }

  public void testFindSelfAssignment() {
    String source = "class A {" +
                    "  protected String s;" +
                    "  A(String t) {" +
                    "    this.s = s;" +
                    "    t = t;" +
                    "    s = this.s;" +
                    "  }" +
                    "}" +
                    "class B extends A {" +
                    "  B(String t) {" +
                    "    super.s = s;" +
                    "  }" +
                    "}";

    String pattern = "'_var='_var";
    assertEquals(4, findMatchesCount(source, pattern));
  }

  public void testFindLambdas() {
    String source = "public interface IntFunction<R> {" +
                    "    R apply(int value);" +
                    "}" +
                    "public interface Function<T, R> {" +
                    "    R apply(T t);" +
                    "}" +
                    "class A {" +
                    "  void m() {" +
                    "    Runnable q = () -> { /*comment*/ };" +
                    "    Runnable r = () -> { System.out.println(); };" +
                    "    IntFunction<String> f = a -> \"hello\";" +
                    "    Function<String, String> g = a -> \"world\";" +
                    "  }" +
                    "}";

    String pattern1 = "() -> {}";
    assertEquals("should find lambdas", 4, findMatchesCount(source, pattern1));

    String pattern2 = "(int '_a) -> {}";
    assertEquals("should find lambdas with specific parameter type", 1, findMatchesCount(source, pattern2));

    String pattern3 = "('_a{0,0})->{}";
    assertEquals("should find lambdas without any parameters", 2, findMatchesCount(source, pattern3));

    String pattern4 = "()->System.out.println()";
    assertEquals("should find lambdas with matching body", 1, findMatchesCount(source, pattern4));

    String pattern5 = "()->{/*comment*/}";
    assertEquals("should find lambdas with comment body", 1, findMatchesCount(source, pattern5));

    String pattern6 = "('_Parameter+) -> System.out.println()";
    assertEquals("should find lambdas with at least one parameter and matching body", 0, findMatchesCount(source, pattern6));
  }

  public void testFindDefaultMethods() {
    String source = "interface XYZ {" +
                    "  default void m() {" +
                    "    System.out.println();" +
                    "  }" +
                    "  void f();" +
                    "  void g();" +
                    "}" +
                    "interface ABC {" +
                    "  void m();" +
                    "}";

    String pattern1 = "interface '_Class {  default '_ReturnType+ 'MethodName+('_ParameterType* '_Parameter*);}";
    assertEquals("should find default method", 1, findMatchesCount(source, pattern1));

    String pattern2 = "interface 'Class {  default '_ReturnType+ '_MethodName{0,0}('_ParameterType* '_Parameter*);}";
    assertEquals("should find interface without default methods", 1, findMatchesCount(source, pattern2));
  }

  public void testFindMethodReferences() {
    String source = "class A {" +
                    "  Runnable r = System.out::println;" +
                    "  Runnable s = this::hashCode;" +
                    "  Runnable t = this::new;" +
                    "  Runnable u = @AA A::new;" +
                    "  static {" +
                    "    System.out.println();" +
                    "  }" +
                    "}";

    String pattern1 = "System . out :: println";
    assertEquals("should find method reference", 1, findMatchesCount(source, pattern1));

    String pattern2 = "this::'_a";
    assertEquals("should find method reference 2", 2, findMatchesCount(source, pattern2));

    String pattern3 = "'_a::'_b";
    assertEquals("should find all method references", 4, findMatchesCount(source, pattern3));

    String pattern4 = "@AA A::new";
    assertEquals("should find annotated method references", 1, findMatchesCount(source, pattern4));
  }

  public void testNoUnexpectedException() {
    String source = "{}";

    try {
      findMatchesCount(source, "/*$A$a*/");
      fail("malformed pattern warning expected");
    } catch (MalformedPatternException ignored) {}

    try {
      findMatchesCount(source, "class $A$Visitor {}");
      fail("malformed pattern warning expected");
    } catch (MalformedPatternException ignored) {
    }

    try {
      String pattern3 = "class $Class$ { \n" +
                        "  class $n$$FieldType$ $FieldName$ = $Init$;\n" +
                        "}";
      findMatchesCount(source, pattern3);
      fail("malformed pattern warning expected");
    } catch (MalformedPatternException ignored) {}

    try {
      findMatchesCount(source, "import java.util.ArrayList;");
      fail("malformed pattern warning expected");
    } catch (MalformedPatternException ignored) {}

    findMatchesCount(source, "'_ReturnType '_Method*('_ParameterType '_Parameter);");
  }

  public void testFindInnerClass() {
    String source = "class Foo {\n" +
                    "  static class Bar {}\n" +
                    "}" +
                    "class A {{" +
                    "  new Foo.Bar();" +
                    "}}";
    String pattern = "new Foo.Bar();";
    assertEquals("should find qualified with outer class", 1, findMatchesCount(source, pattern));
  }

  public void testFindCommentsEverywhere() {
    String source = "abstract class A<T/*1*/> implements java.util.List<T/*2*/>, /*3*/java.io.Serializable {" +
                    "  @SuppressWarnings({\"one\",/*10*/ \"two\"})" +
                    "  public /*11*/ static void m(/*12*/) {" +
                    "    System./*4*/out.println(/*5*/);" +
                    "    A<String/*6*/> a1 = new A(){};" +
                    "    int i = 1 + /*7*/ + 2;" +
                    "    try (java.io.FileInputStream /*8*/in = new java.io.FileInputStream(\"name\")) {" +
                    "    } catch (java.lang./*9*/Exception e) {" +
                    "    }" +
                    "  }" +
                    "}";
    String pattern = "/*$Text$*/";
    assertEquals("should find comments in all the right places", 12, findMatchesCount(source, pattern));
  }

  public void testCaseInsensitive() {
    String source = "/* HELLO */\n" +
                    "class A<T> {\n" +
                    "  private char b = 'C';\n" +
                    "  void m() {\n" +
                    "    @X String s = \"\";\n" +
                    "    s.equals(\"\");\n" +
                    "    s = s;\n" +
                    "    this.b = 'D';\n" +
                    "  }\n" +
                    "}";
    String pattern1 = "a";
    assertEquals("should find symbol case insensitively", 1, findMatchesCount(source, pattern1));
    String pattern2 = "class a {}";
    assertEquals("should find class case insensitively", 1, findMatchesCount(source, pattern2));
    String pattern3 = "/* hello */";
    assertEquals("should find comment case insensitively", 1, findMatchesCount(source, pattern3));
    String pattern4 = "'c'";
    assertEquals("should find character literal case insensitively", 1, findMatchesCount(source, pattern4));
    String pattern5 = "char B = '_initializer;";
    assertEquals("should find variable case insensitively", 1, findMatchesCount(source, pattern5));
    String pattern6 = "class '_a<t> {}";
    assertEquals("should find type parameter case insensitively", 1, findMatchesCount(source, pattern6));
    String pattern7 = "class '_A {" +
                      "  void M();" +
                      "}";
    assertEquals("should find class with method case insensitively", 1, findMatchesCount(source, pattern7));
    String pattern8 = "'_a.EQUALS('_b)";
    assertEquals("should find method call case insensitively", 1, findMatchesCount(source, pattern8));
    String pattern9 = "S.'_call('_e)";
    assertEquals("should find qualifier case insensitively", 1, findMatchesCount(source, pattern9));
    String pattern10 = "S = S";
    assertEquals("should find reference case insensitively", 1, findMatchesCount(source, pattern10));
    String pattern11 = "this.B";
    assertEquals("should find qualified reference case insensitively", 1, findMatchesCount(source, pattern11));
    String pattern12 = "@x";
    assertEquals("should find annotation case insensitively", 1, findMatchesCount(source, pattern12));
  }

  public void testFindTry() {
    String source = "class A {{\n" +
                    "  try (InputStream in = new FileInputStream(\"tmp\")) {\n" +
                    "  }\n" +
                    "  try {\n" +
                    "  } catch (FileNotFoundException e) {\n" +
                    "  } finally {}\n" +
                    "  try {\n" +
                    "  } catch(NullPointerException  | UnsupportedOperationException e) {\n" +
                    "    throw e;\n" +
                    "  } catch(Exception e) {\n" +
                    "     throw new RuntimeException(e);\n" +
                    "  } finally {}\n" +
                    "  try {\n" +
                    "    throw new NoRouteToHostException();\n" +
                    "  } catch (NoRouteToHostException e) {\n" +
                    "    System.out.println();\n" +
                    "  } catch (SocketException e) {\n" +
                    "    System.out.println();\n" +
                    "  } catch (IOException e) {\n" +
                    "  } catch (RuntimeException e) {\n" +
                    "    System.out.println();\n" +
                    "  } finally {}\n" +
                    "}}";

    String pattern1 = "try ('_ResourceType '_Var = '_exp) { '_Statement*; }";
    assertEquals("Find try-with-resources", 1, findMatchesCount(source, pattern1));

    String pattern2 = "try { '_St1*; } catch ('_ExceptionType1 '_e1) { '_St2*; } catch ('_ExceptionType2 '_e2) { '_St3*; }";
    assertEquals("Find try with two or more catch blocks", 2, findMatchesCount(source, pattern2));

    String pattern3 = "try { '_St1*; } finally { '_St2*; }";
    assertEquals("Find try with finally block", 3, findMatchesCount(source, pattern3));

    String pattern4 = "try { '_St1*; } catch (NullPointerException | IllegalArgumentException '_e) { '_St2*; }";
    assertEquals("Match multi catch correctly", 0, findMatchesCount(source, pattern4));

    String pattern5 = "try { '_St1*; } catch (UnsupportedOperationException | NullPointerException '_e) { '_St2*; }";
    assertEquals("Find multi catch", 1, findMatchesCount(source, pattern5));

    String pattern6 = "try { '_St1*; } catch ('_E1 | '_E2 '_e) { '_St2*; }";
    assertEquals("Find multi catch with variables", 1, findMatchesCount(source, pattern6));
  }

  public void testFindAsserts() {
    String source = "class A {" +
                    "  void f(int i) {" +
                    "    assert i > 0;" +
                    "    assert i < 10 : \"i: \" + i;" +
                    "    assert i == 5;" +
                    "  }" +
                    "}";
    assertEquals("find assert statements", 3, findMatchesCount(source, "assert '_a;"));
    assertEquals("find assert statements 2", 3, findMatchesCount(source, "assert '_a : 'b*;"));
    assertEquals("find assert statement with messages", 1, findMatchesCount(source, "assert '_a : '_b;"));
    assertEquals("find assert statement without messages", 2, findMatchesCount(source, "assert 'a : '_b{0,0};"));
  }

  public void testPolyadicExpression() {
    String source = "class A {" +
                    "  void f() {" +
                    "    int i = 1 + 2;" +
                    "    int j = 1 + 2 + 3;" +
                    "    int k = 1 + 2 + 3 + 4;" +
                    "  }" +
                    "}";
    assertEquals("find polyadic expression", 3, findMatchesCount(source, "'_a + '_b+"));
    assertEquals("find polyadic expression of 3 operands", 1, findMatchesCount(source, "'_a + '_b{2,2}"));
    assertEquals("find polyadic expression of >3 operands", 2, findMatchesCount(source, "'_a + '_b{2,100}"));
  }

  public void testMultipleFieldsInOneDeclaration() {
    String source = "class A {" +
                    "  int i;" +
                    "  int j, k;" +
                    "  int l, m, n;" +
                    "  int o, p, q;" +
                    "}";
    assertEquals("find multiple fields in one declaration 1", 3, findMatchesCount(source, "'_a '_b{2,100};"));
    assertEquals("find multiple fields in one declaration 2", 3, findMatchesCount(source, "int '_b{2,100};"));
    assertEquals("find multiple fields in one declaration 2", 2, findMatchesCount(source, "int '_b{3,3};"));
    assertEquals("find declarations with only one field", 1, findMatchesCount(source, "int '_a;"));
    assertEquals("find all declarations", 4, findMatchesCount(source, "int '_a+;"));
    assertEquals("find all fields", 9, findMatchesCount(source, "int 'a+;"));

    String source2 = "class ABC {" +
                     "    String u;" +
                     "    String s,t," +
                     "    void m() {}" +
                     "}";
    assertEquals("find incomplete code", 1, findMatchesCount(source2, "'_a '_b{2,100};"));
  }

  public void testFindWithSimpleMemberPattern() {
    String source  = "class X {" +
                     "  static {}" +
                     "  static {}" +
                     "  static {" +
                     "    System.out.println();" +
                     "  }" +
                     "  void one() {}" +
                     "  void two() {" +
                     "    System.out.println();" +
                     "  }" +
                     "  <T> T three() {" +
                     "    return null;" +
                     "  }" +
                     "}";

    assertEquals("find with simple method pattern", 2, findMatchesCount(source, "void '_a();"));
    assertEquals("find with simple method pattern 2", 1, findMatchesCount(source, "void one();"));
    assertEquals("find with simple method pattern 3", 3, findMatchesCount(source, "'_t '_a('_pt '_p*);"));
    assertEquals("find with simple generic method pattern", 1, findMatchesCount(source, "<'_+> '_Type '_Method('_ '_*);"));
    assertEquals("find with simple static initializer pattern", 3, findMatchesCount(source, "static { '_statement*;}"));
  }

  public void testFindPackageLocalAndInstanceFields() {
    String source = "class X {" +
                    "  final int var1;" +
                    "  void a(final int var2) {" +
                    "    final int var3;" +
                    "  }" +
                    "}";
    assertEquals("parameters and local variables are not package-private", 1, findMatchesCount(source, "@Modifier(\"packageLocal\") '_T '_a;"));
    assertEquals("any variable can be final", 3, findMatchesCount(source, "@Modifier(\"final\") '_T '_a;"));
    assertEquals("parameters and local variables are not instance fields", 1, findMatchesCount(source, "@Modifier(\"Instance\") '_T '_a;"));
  }

  public void testFindParameterizedMethodCalls() {
    String source = "interface Foo {" +
                    "  <T> T bar();" +
                    "  <S, T> void bar2(S, T);" +
                    "}" +
                    "class X {" +
                    "  void x(Foo foo) {" +
                    "    foo.<String>bar();" +
                    "    foo.<Integer>bar();" +
                    "    String s = foo.bar();" +
                    "    foo.bar2(1, 2);" +
                    "  }" +
                    "}";
    assertEquals("find parameterized method calls 1", 1, findMatchesCount(source, "foo.<Integer>bar()"));
    assertEquals("find parameterized method calls 2", 2, findMatchesCount(source, "foo.<String>bar()"));
    assertEquals("find parameterized method calls 3", 3, findMatchesCount(source, "'_a.<'_b>'_c('_d*)"));
    assertEquals("find parameterized method calls 4", 4, findMatchesCount(source, "'_a.<'_b+>'_c('_d*)"));
  }

  public void testFindDiamondTypes() {
    String source = "class A<X, Y> {}" +
                    "class B {{" +
                    "  A<Integer, String> a1 = new A<>();" +
                    "  A<Integer, String> a2 = new A<Integer, String>();" +
                    "  A<Double, Boolean> a3 = new A<>();" +
                    "  A<Double, Boolean> a4 = new A<>();" +
                    "}}";
    assertEquals("find diamond new expressions", 3, findMatchesCount(source, "new A<>()"));
    assertEquals("find parameterized new expressions", 2, findMatchesCount(source, "new A<Integer, String>()"));
  }

  public void testFindSuperCall() {
    String source = "class A {" +
                    "  public String toString() {" +
                    "    System.out.println();" +
                    "    if (false) {" +
                    "      toString();" +
                    "      this.toString();" +
                    "    }" +
                    "    return super.toString();" +
                    "  }" +
                    "}";
    assertEquals("find super call", 1, findMatchesCount(source, "super.'_m()"));
    assertEquals("find super and non super call", 2, findMatchesCount(source, "'_q:[regex( super|this )].'_m()"));

    String source2 = "class A {" +
                     "  public boolean equals(Object o) {" +
                     "    return super.equals(o);" +
                     "  }" +
                     "}";
    assertEquals("find method with super call and matching parameter", 1,
                 findMatchesCount(source2, "'_rt '_m('_t '_p*) { return super.'_m('_p); }"));
  }

  public void testFindWithQualifiers() {
    String source1 = "class Two {" +
                     "  Two x;" +
                     "  void f() {" +
                     "    Two a = x.x.x;" +
                     "    Two b = x.x.x.x;" +
                     "  }" +
                     "}";
    assertEquals(1, findMatchesCount(source1, "x.x.x.'_x"));

    String source2 = "import static java.lang.String.*;" +
                     "class One {" +
                     "  void f() {" +
                     "    valueOf(1);" +
                     "    String.valueOf(1);" +
                     "    java.lang.String.valueOf(1);" +
                     "    Integer.valueOf(1);" +
                     "  }" +
                     "}";
    assertEquals(3, findMatchesCount(source2, "java.lang.String.valueOf(1)"));
    assertEquals(3, findMatchesCount(source2, "String.valueOf(1)"));
    assertEquals(3, findMatchesCount(source2, "'_a?:[regex( String )].valueOf(1)"));
    assertEquals(4, findMatchesCount(source2, "valueOf(1)"));

    String source3 = "class Three {" +
                     "  Three t$;" +
                     "  void f() {" +
                     "    Three a = t$.t$.t$;" +
                     "  }" +
                     "}";
    assertEquals(2, findMatchesCount(source3, "t$.'_t"));
  }

  public void testSearchTypes() {
    String source1 = "import java.util.*;" +
                     "class X {" +
                     "  void x() {" +
                     "    ArrayList<String> fooList = new ArrayList<>();\n" +
                     "    ArrayList<Integer> barList = new ArrayList<>();\n" +
                     "    someStuff(fooList); // find this!\n" +
                     "    someStuff(barList); // don't find this one\n" +
                     "    someStuff(Collections.singletonList(1)); // also match this one\n" +
                     "  }" +
                     "  void someStuff(Iterable<?> param) {}" +
                     "}";
    assertEquals(3, findMatchesCount(source1, "'_Instance?.'_MethodCall:[regex( someStuff )]('_Parameter:[exprtype( *List )])"));
    assertEquals(3, findMatchesCount(source1, "'_Instance?.'_MethodCall:[regex( someStuff )]('_Parameter:[exprtype( *java\\.util\\.List )])"));
    assertEquals(1, findMatchesCount(source1, "'_Instance?.'_MethodCall:[regex( someStuff )]('_Parameter:[exprtype( *List<String> )])"));
    assertEquals(1, findMatchesCount(source1, "'_Instance?.'_MethodCall:[regex( someStuff )]('_Parameter:[exprtype( *java\\.util\\.List<java\\.lang\\.String> )])"));
    assertEquals(2, findMatchesCount(source1, "'_Instance?.'_MethodCall:[regex( someStuff )]('_Parameter:[exprtype( *List<Integer> )])"));
    assertEquals(2, findMatchesCount(source1, "'_Instance?.'_MethodCall:[regex( someStuff )]('_Parameter:[exprtype( *java\\.util\\.List<java\\.lang\\.Integer> )])"));

    String source2 = "class X {" +
                     "  String sss[][];" +
                     "  String ss[];" +
                     "  void x() {" +
                     "    System.out.println(sss);" +
                     "  }" +
                     "}";
    assertEquals(1, findMatchesCount(source2, "'_x:[exprtype( String\\[\\]\\[\\] )]"));
    assertEquals(1, findMatchesCount(source2, "'_x:[exprtype( java\\.lang\\.String\\[\\]\\[\\] )]"));

    String source3 = "import java.util.*;" +
                     "class X {" +
                     "  void x(Map.Entry<String, Integer> map) {" +
                     "    System.out.println(map);" +
                     "  }" +
                     "}";
    assertEquals(1, findMatchesCount(source3, "'_x:[exprtype( Map\\.Entry<String,Integer> )]"));
    assertEquals(1, findMatchesCount(source3, "'_x:[exprtype( Entry<String,Integer> )]"));
    assertEquals(1, findMatchesCount(source3, "'_x:[exprtype( Map\\.Entry )]"));
    assertEquals(1, findMatchesCount(source3, "'_x:[exprtype( Entry )]"));
    assertEquals(1, findMatchesCount(source3, "'_x:[exprtype( java\\.util\\.Map\\.Entry )]"));
    assertEquals(1, findMatchesCount(source3, "'_x:[exprtype( java\\.util\\.Map\\.Entry<java\\.lang\\.String,java\\.lang\\.Integer> )]"));

    String source4 = "import java.util.*;" +
                     "class X {" +
                     "  void x() {" +
                     "    new AbstractList<String>() {" +
                     "      @Override" +
                     "      public int size() {" +
                     "        return 0;" +
                     "      }" +
                     "      @Override" +
                     "      public String get(int index) {" +
                     "        return null;" +
                     "      }\n" +
                     "    };" +
                     "  }" +
                     "}";
    assertEquals(1, findMatchesCount(source4, "'x:[exprtype( *List )]"));
    assertEquals(1, findMatchesCount(source4, "'x:[exprtype( *List<String> )]"));
    assertEquals(1, findMatchesCount(source4, "'x:[exprtype( AbstractList )]"));
    assertEquals(1, findMatchesCount(source4, "'x:[exprtype( AbstractList<String> )]"));
    assertEquals(0, findMatchesCount(source4, "'x:[exprtype( AbstractList<Integer> )]"));

    String source5 = "class X {" +
                     "  void x() {" +
                     "    new UnknownStranger<Johnny5>() {};" +
                     "  }" +
                     "}";
    assertEquals(1, findMatchesCount(source5, "'x:[exprtype( UnknownStranger )]"));
    assertEquals(1, findMatchesCount(source5, "'x:[exprtype( UnknownStranger<Johnny5> )]"));

    String source6 = "class X {" +
                     "  List<List<String>> list;" +
                     "  List<Garbage> list2;" +
                     "  List<Garbage> list3;" +
                     "  void x() {" +
                     "    System.out.println(list);" +
                     "    System.out.println(list2);" +
                     "    System.out.println(list3);" +
                     "  }" +
                     "}";
    assertEquals(3, findMatchesCount(source6, "'x:[exprtype( List )]"));
    assertEquals(1, findMatchesCount(source6, "'x:[exprtype( List<List<String>> )]"));
    assertEquals(2, findMatchesCount(source6, "'x:[exprtype( List<Garbage> )]"));
  }
}
