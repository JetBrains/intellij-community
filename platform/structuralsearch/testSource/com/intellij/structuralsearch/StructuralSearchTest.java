/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
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
  private static final String s1 =
    "debug(\"In action performed:\"+event);"+
    "project = (Project)event.getDataContext().getData(DataConstants.PROJECT);" +
    "CodeEditorManager.getInstance(project).commitAllToPsiFile();" +
    "file = (PsiFile) event.getDataContext().getData(\"psi.File\"); " +
    "((dialog==null)?" +
    "  (dialog = new SearchDialog()):" +
    "  dialog" +
    ").show();";

  private static final String s2 = "((dialog==null)? (dialog = new SearchDialog()): dialog).show();";
  private static final String s3 = "dialog = new SearchDialog()";

  private static final String s4 =
                " do { " +
                "  pattern = pattern.getNextSibling(); " +
                " } " +
                " while (pattern!=null && filterLexicalNodes(pattern));";

  private static final String s5 =
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
                "}";

  private static final String s6 =
                " do { " +
                "  pattern.getNextSibling(); " +
                " } " +
                " while (pattern!=null && filterLexicalNodes(pattern));";

  private static final String s7 =
                " if (true) throw new UnsupportedPatternException(statement.toString());" +
                " if (true) { " +
                "   throw new UnsupportedPatternException(statement.toString());" +
                " } ";

  private static final String s8 =
                " if (true) { " +
                "   throw new UnsupportedPatternException(statement.toString());" +
                " } ";

  private static final String s9 = " if (true) throw new UnsupportedPatternException(statement.toString());";

  private static final String s10 = "listener.add(new Runnable() { public void run() {} });";
  private static final String s11 = " new XXX()";

  private static final String s12 =
                 "new Runnable() {" +
                 "  public void run() {" +
                 "   matchContext.getSink().matchingFinished();" +
                 "   } " +
                 " }";

  private static final String s13 = "new Runnable() {}";
  private static final String s14_1 = "if (true) { aaa(var); }";
  private static final String s14_2 = "if (true) { aaa(var); bbb(var2); }\n if(1==1) { system.out.println('o'); }";
  private static final String s15 = "'T;";
  private static final String s16 = "if('_T) { '_T2; }";
  private static final String s17 =
    "token.getText().equals(token2.getText());" +
    "token.getText().equals(token2.getText2());" +
    "token.a.equals(token2.b);" +
    "token.a.equals(token2.a);";
  private static final String s18_1 = "'_T1.'_T2.equals('_T3.'_T2);";
  private static final String s18_2 = "'_T1.'_T2().equals('_T3.'_T2());";
  private static final String s18_3 = "'_T1.'_T2";
  private static final String s19 = "Aaa a = (Aaa)b; Aaa c = (Bbb)d;";
  private static final String s20 = "'_T1 'T2 = ('_T1)'_T3;";
  private static final String s20_2 = "'_T1 '_T2 = ('_T1)'_T3;";
  private static final String s21_1 = "'_T1:Aa* 'T2 = ('_T1)'_T3;";
  private static final String s21_2 = "'_T1:A* 'T2 = ( '_T1:A+ )'_T3;";
  private static final String s21_3 = "'_T1:Aa* 'T2 = ( '_T1 )'_T3;";

  private static final String s22 = "Aaa a = (Aaa)b; Bbb c = (Bbb)d;";

  private static final String s23 = "a[i] = 1; b[a[i]] = f(); if (a[i]==1) return b[c[i]];";
  private static final String s24_1 = "'T['_T2:.*i.* ] = '_T3;";
  private static final String s24_2  = "'T['_T2:.*i.* ]";
  private static final String s25  = "class MatcherImpl {  void doMatch(int a) {} }\n" +
                                     "class Matcher { abstract void doMatch(int a);}\n " +
                                     "class Matcher2Impl { void doMatch(int a, int b) {} } ";
  private static final String s26  = "class 'T:.*Impl { '_T2 '_T3('_T4 '_T5) {\n\n} } ";
  private static final String s27 = "class A {} interface B {}";
  private static final String s28 = "interface 'T {}";

  private static final String s29 = "class A { void B(int C) {} } class D { void E(double e) {} }";
  private static final String s30 = "class '_ { void '_('_:int '_); } ";

  private static final String s31 = "class A extends B { } class D extends B { } class C extends C {}";
  private static final String s32 = "class '_ extends B {  } ";

  private static final String s33 = "class A implements B,C { } class D implements B,D { } class C2 implements C,B {}";
  private static final String s34 = "class '_ implements B,C {  } ";

  private static final String s35 = "class A { int b; double c; void d() {} int e() {} } " +
                                    "class A2 { int b; void d() {} }";
  private static final String s36 = "class '_ { double '_; int '_; int '_() {} void '_() {} } ";

  private static final String s37 = "class A { void d() throws B,C,D {} } class A2 { void d() throws B,C {} }";
  private static final String s38 = "class 'T { '_ '_() throws D,C {} } ";

  private static final String s39 = "class A extends B { } class A2 {  }";
  private static final String s40 = "class 'T { } ";

  private static final String s41 = "class A extends B { int a = 1; } class B { int[] c= new int[2]; } " +
                                    "class D { double e; } class E { int d; } ";
  private static final String s42_1 = "class '_ { '_T '_T2 = '_T3; } ";
  private static final String s42_2 = "class '_ { '_T '_T2; } ";

  private static final String s43 = "interface A extends B { int B = 1; } " +
                                    "interface D { public final static double e = 1; } " +
                                    "interface E { final static ind d = 2; } " +
                                    "interface F {  } ";
  private static final String s44 = "interface '_ { '_T 'T2 = '_T3; } ";
  private static final String s45 = "class A extends B { private static final int B = 1; } " +
                                    "class C extends D { int B = 1; }" +
                                    "class E { }";

  private static final String s46 = "class '_ { final static private '_T 'T2 = '_T3; } ";
  private static final String s46_2 = "class '_ { '_T 'T2 = '_T3; } ";

  private static final String s47 = "class C { java.lang.String t; } class B { BufferedString t2;} class A { String p;} ";
  private static final String s48 = "class '_ { String '_; }";

  private static final String s49 = "class C { void a() throws java.lang.RuntimeException {} } class B { BufferedString t2;}";
  private static final String s50 = "class '_ { '_ '_() throws RuntimeException; }";

  private static final String s51 = "class C extends B { } class B extends A { } class E {}";
  private static final String s52 = "class '_ extends '_ {  }";

  private static final String s53 = "class C { " +
                                    "   String a = System.getProperty(\"abcd\"); " +
                                    "  static { String s = System.getProperty(a); }" +
                                    "  static void b() { String s = System.getProperty(a); }" +
                                    " }";
  private static final String s54 = "System.getProperty('T)";

  private static final String s55 = " a = b.class; ";
  private static final String s56 = "'T.class";

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
  private static final String s57_2 = "/** @author Maxim */ class C { " +
                                      "} " +
                                      "class D {" +
                                      "/** @serializable */ private int value; " +
                                      "/** @since 1.4 */ void a() {} "+
                                      "}" +
                                      "class F { " +
                                      "/** @since 1.4 */ void a() {} "+
                                      "/** @serializable */ private int value2; " +
                                      "}" +
                                      "class G { /** @param a*/ void a() {} }";
  private static final String s58 = "/** @'T '_T2 */ class '_ { }";
  private static final String s58_2 = "class '_ { /** @serializable '_* */ '_ '_; }";
  private static final String s58_3 = "class '_ { /** @'T 1.4 */ '_ '_() {} }";
  private static final String s58_4 = "/** @'T '_T2 */";
  private static final String s58_5 = "/** @'T '_T2? */";

  private static final String s59 = "interface A { void B(); }";
  private static final String s60 = "interface '_ { void '_(); }";

  private static final String s61 = "{ a=b; c=d; return; } { e=f; } {}";
  private static final String s62_1 = "{ 'T*; }";
  private static final String s62_2 = "{ 'T+; }";
  private static final String s62_3 = "{ 'T?; }";

  private static final String s62_4 = "{ '_*; }";
  private static final String s62_5 = "{ '_+; }";
  private static final String s62_6 = "{ '_?; }";

  private static final String s63 = " class A { A() {} } class B { public void run() {} }";
  private static final String s63_2 = " class A { A() {} " +
                                      "class B { public void run() {} } " +
                                      "class D { public void run() {} } " +
                                      "} " +
                                      "class C {}";
  private static final String s64 = " class 'T { public void '_T2:run () {} }";
  private static final String s64_2 = "class '_ { class 'T { public void '_T2:run () {} } }";

  private static final String s65 = " if (A instanceof B) {} else if (B instanceof C) {}";
  private static final String s66 = " '_T instanceof '_T2:B";

  private static final String s67 = " buf.append((VirtualFile)a);";
  private static final String s68 = " (VirtualFile)'T";

  private static final String s69 = " System.getProperties(); System.out.println(); java.lang.System.out.println(); some.other.System.out.println();";
  private static final String s70 = " System.out ";
  private static final String s70_2 = " java.lang.System.out ";

  private static final String s71 = " class A { " +
                                    "class D { D() { c(); } }" +
                                    "void a() { c(); new MouseListenener() { void b() { c(); } } }" +
                                    " }";
  private static final String s72 = " c(); ";

  private static final String s73 = " class A { int A; static int B=5; public abstract void a(int c); void q() { ind d=7; } }";
  private static final String s74 = " '_Type 'Var = '_Init?; ";
  private static final String s75 = "/** @class aClass\n @author the author */ class A {}\n" +
                                    "/** */ class B {}\n" +
                                    "/** @class aClass */ class C {}";
  private static final String s76 = " /** @'_tag+ '_value+ */";
  private static final String s76_2 = " /** @'_tag* '_value* */";
  private static final String s76_3 = " /** @'_tag? '_value? */ class 't {}";

  private static final String s77 = " new ActionListener() {} ";
  private static final String s78 = " class 'T:.*aaa {} ";

  private static final String s79 = " class A { static { int c; } void a() { int b; b=1; }} ";
  private static final String s80 = " { '_T 'T3 = '_T2?; '_*; } ";

  private static final String s81 = "class Pair<First,Second> {" +
                                    "  <C,F> void a(B<C> b, D<F> e) throws C {" +
                                    "    P<Q> r = (S<T>)null;"+
                                    "    Q q = null; "+
                                    "    if (r instanceof S<T>) {}"+
                                    "  } " +
                                    "} class Q { void b() {} } ";

  private static final String s81_2 = "class Double<T> {} class T {} class Single<First extends A & B> {}";

  private static final String s82 = "class '_<'T+> {}";
  private static final String s82_2 = "'_Expr instanceof '_Type<'_Parameter+>";
  private static final String s82_3 = "( '_Type<'_Parameter+> ) '_Expr";
  private static final String s82_4 = "'_Type<'_Parameter+> 'a = '_Init?;";
  private static final String s82_5 = "class '_ { <'_+> '_Type 'Method('_* '_*); }";
  private static final String s82_6 = "class '_<'_+ extends 'res+> {}";
  private static final String s82_7 = "'Type";

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

    private static final String s84 = "    /**\n" +
                                      "     * @hibernate.property\n" +
                                      "     *  'Property+\n" +
                                      "     */\n";

  private static final String s84_2 = "    /**\n" +
                                      "     * @hibernate.property\n" +
                                      "     *  update=\"fa.se\"\n" +
                                      "     */\n";

  private static final String s85 = "{ int a; a=1; a=1; return a; }";
  private static final String s86 = "'T; 'T;";

  private static final String s87 = " getSomething(\"1\"); a.call(); ";
  private static final String s88 = " '_Instance.'Call('_*); ";
  private static final String s88_2 = " 'Call('_*); ";
  private static final String s88_3 = " '_Instance?.'Call('_*); ";
  private static final String s89 = "{ a = 1; b = 2; c=3; }";
  private static final String s90 = "{ '_T*; '_T2*; }";
  private static final String s90_2 = " { '_T*; '_T2*; '_T3+; } ";
  private static final String s90_3 = " { '_T+; '_T2+; '_T3+; '_T4+; } ";
  private static final String s90_4 = " { '_T{1,3}; '_T2{2}; } ";
  private static final String s90_5 = " { '_T{1}?; '_T2*?; '_T3+?; } ";
  private static final String s90_6 = " { '_T{1}?; '_T2{1,2}?; '_T3+?; '_T4+?; } ";

  private static final String s91 = "class a {\n" +
                                    "  void b() {\n" +
                                    "    int c;\n" +
                                    "\n" +
                                    "    c = 1;\n" +
                                    "    b();\n" +
                                    "    a a1;\n" +
                                    "  }\n" +
                                    "}";
  private static final String s92 = "'T:a";
  private static final String s92_2 = "'T:b";
  private static final String s92_3 = "'T:c";

  private static final String s93 = " class A {" +
                                    "private int field;" +
                                    "public void b() {}" +
                                    "}";
  private static final String s94 = " class '_ {" +
                                    "private void b() {}" +
                                    "}";
  private static final String s94_2 = " class '_ {" +
                                      "public void b() {}" +
                                      "}";
  private static final String s94_3 = " class '_ {" +
                                      "protected int field;" +
                                      "}";
  private static final String s94_4 = " class '_ {" +
                                      "private int field;" +
                                      "}";

  private static final String s95 = " class Clazz {" +
                                    "private int field;" +
                                    "private int field2;" +
                                    "private int fiel-d2;" +
                                    "}";

  private static final String  s96 = " class '_ {" +
                                     "private int 'T+:field.* ;" +
                                     "}";

  public void testSearchExpressions() {
    assertFalse("subexpr match",findMatchesCount(s2,s3)==0);
    assertEquals("search for new ",findMatchesCount(s10,s11),0);
    assertEquals("search for anonymous classes",findMatchesCount(s12,s13),1);
    // expr in definition initializer
    assertEquals(
      "expr in def initializer",
      3,
      findMatchesCount(s53,s54)
    );

    // a.class expression search
    assertEquals(
      "a.class pattern",
      findMatchesCount(s55,s56),
      1
    );

    String complexCode = "interface I { void b(); } interface I2 extends I {} class I3 extends I {} " +
                         "class A implements I2 {  void b() {} } class B implements I3 { void b() {}} " +
                         "I2 a; I3 b; a.b(); b.b(); b.b(); A c; B d; c.b(); d.b(); d.b(); ";

    String exprTypePattern1 = "'t:[exprtype( I2 )].b();";
    String exprTypePattern2 = "'t:[!exprtype( I2 )].b();";

    String exprTypePattern3 = "'t:[exprtype( *I2 )].b();";
    String exprTypePattern4 = "'t:[!exprtype( *I2 )].b();";

    assertEquals(
      "expr type condition",
      findMatchesCount(complexCode,exprTypePattern1),
      1
    );

    assertEquals(
      "expr type condition 2",
      5,
      findMatchesCount(complexCode,exprTypePattern2)
    );

    assertEquals(
      "expr type condition 3",
      findMatchesCount(complexCode,exprTypePattern3),
      2
    );

    assertEquals(
      "expr type condition 4",
      findMatchesCount(complexCode,exprTypePattern4),
      4
    );

    String complexCode2 = "enum X { XXX, YYY }\n class C { static void ordinal() {} void test() { C c; c.ordinal(); c.ordinal(); X.XXX.ordinal(); } }";
    assertEquals(
      "expr type condition with enums",
      findMatchesCount(complexCode2, "'t:[exprtype( *java\\.lang\\.Enum )].ordinal()"),
      1
    );

    assertEquals(
      "no smart detection of search target",
      findMatchesCount("processInheritors(1,2,3,4); processInheritors(1,2,3); processInheritors(1,2,3,4,5,6);","'instance?.processInheritors('_param1{1,6});"),
      3
    );

    String arrays = "int[] a = new int[20];\n" +
                    "byte[] b = new byte[30]";
    String arrayPattern = "new int[$a$]";
    assertEquals(
      "Improper array search",
      1,
      findMatchesCount(arrays,arrayPattern)
    );

    String someCode = "a *= 2; a+=2;";
    String otherCode = "a *= 2;";

    assertEquals(
      "Improper *= 2 search",
      1,
      findMatchesCount(someCode,otherCode)
    );

    String s1 = "Thread t = new Thread(\"my thread\",\"my another thread\") {\n" +
                "    public void run() {\n" +
                "        // do stuff\n" +
                "    }\n" +
                "}";
    String s2 = "new Thread('args*) { '_Other* }";

    assertEquals(
      "Find inner class parameters",
      2,
      findMatchesCount(s1,s2)
    );

    String s3 = "Thread t = new Thread(\"my thread\") {\n" +
                "    public void run() {\n" +
                "        // do stuff\n" +
                "    }\n" +
                "};";
    String s4 = "new Thread($args$)";

    assertEquals(
      "Find inner class by new",
      1,
      findMatchesCount(s3,s4)
    );

    String s5 = "class A {\n" +
                "public static <T> T[] copy(T[] array, Class<T> aClass) {\n" +
                "    int i = (int)0;\n" +
                "    int b = (int)0;\n" +
                "    return (T[])array.clone();\n" +
                "  }\n" +
                "}";
    String s6 = "($T$[])$expr$";

    assertEquals(
      "Find cast to array",
      1,
      findMatchesCount(s5,s6)
    );

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
    String s8 = "'_Instance.'_MethodCall:[regex( prorate )]('_Param:[exprtype( BigDecimal\\[\\] )]) ";

    assertEquals(
      "Find method call with array for parameter expr type",
      2,
      findMatchesCount(s7,s8,true)
    );

    String s13 = "try { } catch(Exception e) { e.printStackTrace(); }";
    String s14 = "'_Instance.'_MethodCall('_Parameter*)";

    assertEquals(
      "Find statement in catch",
      1,
      findMatchesCount(s13,s14)
    );

    String s9 = "int a[] = new int[] { 1,2,3,4};\n" +
                "int b[] = { 2,3,4,5 };\n" +
                "Object[] c = new Object[] { \"\", null};\n" +
                "Object[] d = {null, null};\n" +
                "Object[] e = {};\n" +
                "Object[] f = new Object[]{}\n" +
                "String[] g = new String[]{}\n" +
                "String[] h = new String[]{new String()}";

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

    String s10 = "int time = 99;\n" +
                 "String str = time < 0 ? \"\" : \"\";" +
                 "String str2 = time < time ? \"\" : \"\";";

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
    String s2 = "static String '_FieldName = '_Init?:[!regex( \".*\" )];";
    String s2_2 = "static String '_FieldName = '_Init:[!regex( \".*\" )];";

    assertEquals(
      "Literal",
      2,
      findMatchesCount(s,s2)
    );

    assertEquals(
      "Literal, 2",
      1,
      findMatchesCount(s,s2_2)
    );

    String pattern3 = "\"'String\"";
    assertEquals("String literal", 1, findMatchesCount(s, pattern3));

    String pattern4 = "\"test\"";
    String source = "@SuppressWarnings(\"test\") class A {" +
                    "  @SuppressWarnings({\"other\", \"test\"}) String field;" +
                    "}";
    assertEquals("String literal in annotation", 2, findMatchesCount(source, pattern4));
  }

  public void testCovariantArraySearch() {
    String s1 = "String[] argv;";
    String s2 = "String argv;";
    String s3 = "'T[] argv;";
    String s3_2 = "'T:*Object [] argv;";

    assertEquals(
      "Find array types",
      0,
      findMatchesCount(s1,s2)
    );

    assertEquals(
      "Find array types, 2",
      0,
      findMatchesCount(s2,s1)
    );

    assertEquals(
      "Find array types, 3",
      0,
      findMatchesCount(s2,s3)
    );

    assertEquals(
      "Find array types, 3",
      1,
      findMatchesCount(s1,s3_2)
    );

    String s11 = "class A {\n" +
                 "  void main(String[] argv);" +
                 "  void main(String argv[]);" +
                 "  void main(String argv);" +
                 "}";
    String s12 = "'_t:[regex( *Object\\[\\] ) ] '_t2;";
    String s12_2 = "'_t:[regex( *Object ) ] '_t2 [];";
    String s12_3 = "'_t:[regex( *Object ) ] '_t2;";

    assertEquals(
      "Find array covariant types",
      2,
      findMatchesCount(s11,s12)
    );

    assertEquals(
      "Find array covariant types, 2",
      2,
      findMatchesCount(s11,s12_2)
    );

    assertEquals(
      "Find array covariant types, 3",
      1,
      findMatchesCount(s11,s12_3)
    );
  }

  public void testFindArrayDeclarations() {
    String source = "class A {" +
                    "  String ss[][];" +
                    "  int f()[] {" +
                    "    return null;" +
                    "  }" +
                    "}";

    String target = "String[][] $s$;";
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

  // @todo support back references (\1 in another reg exp or as fild member)
  //private static final String s1002 = " setSSS( instance.getSSS() ); " +
  //                                    " setSSS( instance.SSS ); ";
  //private static final String s1003 = " 't:set(.+) ( '_.get't_1() ); ";
  //private static final String s1003_2 = " 't:set(.+) ( '_.'t_1 ); ";

  public void testSearchStatements() {
    assertEquals("statement search",findMatchesCount(s1,s2),1);
    assertEquals("several constructions match",findMatchesCount(s5,s4),3);
    assertFalse("several constructions 2",(findMatchesCount(s5,s6))!=0);

    assertEquals("several constructions 3",findMatchesCount(s7,s8),2);
    assertEquals("several constructions 4",findMatchesCount(s7,s9),2);

    final String s1000 = "{ lastTest = \"search for parameterized pattern\";\n" +
                         "      matches = testMatcher.findMatches(s14_1,s15, options);\n" +
                         "      if (matches.size()!=2 ) return false;\n" +
                         "lastTest = \"search for parameterized pattern\";\n" +
                         "      matches = testMatcher.findMatches(s14_1,s15, options);\n" +
                         "      if (matches.size()!=2 ) return false; }";
    final String s1001 = "lastTest = '_Descr; " +
                         "      matches = testMatcher.findMatches('_In,'_Pattern, options);\n" +
                         "      if (matches.size()!='_Number ) return false;";

    assertEquals("several operators 5",findMatchesCount(s1000,s1001),2);

    assertEquals(
      "two the same statements search",
      findMatchesCount(s85,s86),
      1
    );

    assertEquals(
      "search for simple call",
      findMatchesCount(s87,s88),
      1
    );

    assertEquals(
      "search for simple call 2",
      findMatchesCount(s87,s88_2),
      1
    );

    assertEquals(
      "search for simple call 3",
      findMatchesCount(s87,s88_3),
      2
    );

    String s10015 = "DocumentListener[] listeners = getCachedListeners();";
    String s10016 = "'_Type 'Var = '_Call();";

    assertEquals(
      "search for definition with init",
      1,
      findMatchesCount(s10015,s10016)
    );

    String s10017 = "a = b; b = c; a=a; c=c;";
    String s10018 = "'_a = '_a;";

    assertEquals(
      "search silly assignments",
      2,
      findMatchesCount(s10017,s10018)
    );

    String s10019 = "a.b(); a.b(null); a.b(null, 1);";
    String s10020 = "a.b(null);";

    assertEquals(
      "search parameter",
      1,
      findMatchesCount(s10019,s10020)
    );

    String s1008 = "int a, b, c, d; int a,b,c; int c,d; int e;";
    String s1009 = "int '_a{3,4};";

    assertEquals(
      "search many declarations",
      2,
      findMatchesCount(s1008,s1009)
    );

    String s1 = "super(1,1);  call(1,1); call(2,2);";
    String s2 = "super('_t*);";

    assertEquals(
      "search super",
      1,
      findMatchesCount(s1,s2)
    );

    String s10021 = "short a = 1;\n" +
                    "short b = 2;\n" +
                    "short c = a.b();";
    String s10022 = "short '_a = '_b.b();";

    assertEquals(
      "search def init bug",
      1,
      findMatchesCount(s10021,s10022)
    );

    String s10023 = "abstract class A { public abstract short getType(); }\n" +
                    "A a;\n" +
                    "switch(a.getType()) {\n" +
                    "  default:\n" +
                    "  return 0;\n" +
                    "}\n" +
                    "switch(a.getType()) {\n" +
                    "  case 1:\n" +
                    "  { return 0; }\n" +
                    "}";
    String s10024 = "switch('_a:[exprtype( short )]) { '_statement*; }";
    assertEquals(
      "finding switch",
      2,
      findMatchesCount(s10023,s10024)
    );

    String s10025 = "A[] a;\n" +
                    "A b[];\n" +
                    "A c;";
    String s10026 = "A[] 'a;";
    String s10026_2 = "A 'a[];";

    assertEquals(
      "array types in dcl",
      2,
      findMatchesCount(s10025,s10026)
    );

    assertEquals(
      "array types in dcl 2",
      2,
      findMatchesCount(s10025,s10026_2)
    );

    String s10027 = "try { a(); } catch(Exception ex) {}\n" +
                    "try { a(); } finally {}\n" +
                    "try { a(); } catch(Exception ex) {} finally {} \n";
    String s10028 = "try { a(); } finally {}\n";
    assertEquals(
      "finally matching",
      2,
      findMatchesCount(s10027,s10028)
    );

    String s10029 = "for(String a:b) { System.out.println(a); }";
    String s10030 = "for(String a:b) { '_a; }";
    assertEquals(
      "for each matching",
      1,
      findMatchesCount(s10029,s10030)
    );

    String s10031 = "try { a(); } catch(Exception ex) {} catch(Error error) { 1=1; }\n" +
                    "try { a(); } catch(Exception ex) {}";
    String s10032 = "try { a(); } catch('_Type+ 'Arg+) { '_Statements*; }\n";
    assertEquals(
      "finally matching",
      2,
      findMatchesCount(s10031,s10032)
    );

    String s10033 = "return x;\n" +
                    "return !x;\n" +
                    "return (x);\n" +
                    "return (x);\n" +
                    "return !(x);";
    String s10034 = "return ('a);";
    assertEquals("Find statement with parenthesized expr",2,findMatchesCount(s10033,s10034));

    String in = "if (true) {" +
                "  System.out.println();" +
                "} else {" +
                "  System.out.println();" +
                "}" +
                "if (true) System.out.println();";
    String pattern1 = "if ('_exp) { '_statement*; }";
    assertEquals("Find if statement with else", 2, findMatchesCount(in, pattern1));

    String pattern2 = "if ('_exp) { '_statement*; } else { '_statement2{0,0}; }";
    assertEquals("Find if statement without else", 1, findMatchesCount(in, pattern2));
  }

  public void testSearchClass() {
    // no modifier list in interface vars
    assertEquals(
      "no modifier for interface vars",
      findMatchesCount(s43,s44),
      3
    );

    // different order of access modifiers
    assertEquals(
      "different order of access modifiers",
      findMatchesCount(s45,s46),
      1
    );

    // no access modifiers
    assertEquals(
      "no access modifier",
      findMatchesCount(s45,s46_2),
      2
    );

    // type could differ with package
    assertEquals(
      "type differs with package",
      findMatchesCount(s47,s48),
      2
    );

    // reference element could differ in package
    assertEquals(
      "reference could differ in package",
      findMatchesCount(s49,s50),
      1
    );

    String s51 = "class C extends java.awt.List {} class A extends java.util.List {} class B extends java.awt.List {} ";
    String s52 = "class 'B extends '_C:java\\.awt\\.List {}";

    assertEquals(
      "reference could differ in package 2",
      findMatchesCount(s51,s52),
      2
    );

    assertEquals(
      "method access modifier",
      findMatchesCount(s93,s94),
      0
    );

    assertEquals(
      "method access modifier 2",
      findMatchesCount(s93,s94_2),
      1
    );

    assertEquals(
      "field access modifier",
      findMatchesCount(s93,s94_3),
      0
    );

    assertEquals(
      "field access modifier 2",
      findMatchesCount(s93,s94_4),
      1
    );

    final String s127 = "class a { void b() { new c() {}; } }";
      final String s128 = "class 't {}";
    assertEquals(
      "class finds anonymous class",
      findMatchesCount(s127,s128),
      2
    );

    final String s129 = "class a { public void run() {} }\n" +
                        "class a2 { public void run() { run(); } }\n" +
                        "class a3 { public void run() { run(); } }\n" +
                        "class a4 { public void run(); }";

    final String s130 = "class 'a { public void run() {} }";
    final String s130_2 = "class 'a { public void run() { '_statement; } }";
    final String s130_3 = "class 'a { public void run(); }";

    assertEquals(
      "empty method finds empty method only",
      findMatchesCount(s129,s130),
      1
    );

    assertEquals(
      "nonempty method finds nonempty method",
      findMatchesCount(s129,s130_2),
      2
    );

    assertEquals(
      "nonempty method finds nonempty method",
      findMatchesCount(s129,s130_3),
      4
    );

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
    assertEquals(
      "reject method with 0 max occurence",
      findMatchesCount(s135,s136),
      1
    );

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
    final String s138 = "class 'm {\n" +
                        "    Project '_f{0,0} = '_t?;\n" +
                        "}";
    assertEquals(
      "reject field with 0 max occurence",
      findMatchesCount(s137,s138),
      2
    );

    final String s139 = "class My { boolean equals(Object o); int hashCode(); }";
    final String s139_2 = "class My { boolean equals(Object o); }";
    final String s140 = "class 'A { boolean equals(Object '_o ); int '_hashCode{0,0}:hashCode (); }";

    assertEquals(
      "reject method with constraint",
      findMatchesCount(s139,s140),
      0
    );

    assertEquals(
      "reject field with 0 max occurence",
      findMatchesCount(s139_2,s140),
      1
    );

    final String s141 = "class A { static { a = 10 } }\n" +
                        "class B { { a = 10; } }\n" +
                        "class C { { a = 10; } }";
    final String s142 = "class '_ { static { a = 10; } } ";
    assertEquals(
      "static block search",
      findMatchesCount(s141,s142),
      1
    );
  }

  public void testParameterlessContructorSearch() {
    final String s143 = "class A { A() {} };\n" +
                        "class B { B(int a) {} };\n" +
                        "class C { C() {} C(int a) {} };\n" +
                        "class D {}\n" +
                        "class E {}";
    final String s144 = "class '_a { '_d{0,0}:[ script( \"__context__.constructor\" ) ]('_b+ '_c+); }";
    assertEquals(
      "parameterless contructor search",
      3,
      findMatchesCount(s143,s144)
    );
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
                 findMatchesCount(source2,
                                  "'_a + '_b:[script(\"__log__.info(b)\n__log__.info(__context__)\ntrue\")]"));
  }

  public void testCheckScriptValidation() {
    final String s1 = "";
    final String s2 = "'_b:[script( \"^^^\" )]";

    try {
      final int count = findMatchesCount(s1, s2);
      assertFalse("Validation does not work", true);
    } catch (MalformedPatternException ex) {}
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

    assertEquals(
      "expr type with object",
      4,
      findMatchesCount(s1,s2,true)
    );
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
    String what = "class 'A implements '_B:*Serializable , '_C:*Cloneable {}";
    assertEquals(
      "search interface within hierarchy",
      5,
      findMatchesCount(in, what)
    );
  }

  public void testSearchBacktracking() {
    assertEquals(
      "backtracking greedy regexp",
      findMatchesCount(s89,s90),
      1
    );

    assertEquals(
      "backtracking greedy regexp 2",
      findMatchesCount(s89,s90_2),
      1
    );

    assertEquals(
      "backtracking greedy regexp 3",
      findMatchesCount(s89,s90_3),
      0
    );

    assertEquals(
      "counted regexp (with back tracking)",
      findMatchesCount(s89,s90_4),
      1
    );

    assertEquals(
      "nongreedy regexp (counted, with back tracking)",
      findMatchesCount(s89,s90_5),
      1
    );

    assertEquals(
      "nongreedy regexp (counted, with back tracking) 2",
      findMatchesCount(s89,s90_6),
      0
    );

    String s1000 = "class A {\n" +
                   "      void _() {}\n" +
                   "      void a(String in, String pattern) {}\n" +
                   "    }";
    String s1001 = "class '_Class { \n" +
                   "  '_ReturnType+ 'MethodName+ ('_ParameterType* '_Parameter* );\n" +
                   "}";
    assertEquals(
      "handling of no match",
      findMatchesCount(s1000,s1001),
      2
    );
  }

  public void testSearchSymbol() {
    final String s131 = "a.b(); c.d = 1; ";
    final String s132 = "'T:b|d";

    assertEquals(
      "symbol match",
      2,
      findMatchesCount(s131,s132)
    );

    final String s129 = "A a = new A();";
    final String s130 = "'Sym:A";

    options.setCaseSensitiveMatch(true);
    assertEquals(
      "case sensitive match",
      findMatchesCount(s129,s130),
      2
    );

    final String s133 = "class C { int a; int A() { a = 1; }} void c(int a) { a = 2; }";
    final String s133_2 = "class C { int a() {} int A() { a(1); }}";
    final String s134 = "a";

    List<MatchResult> results = findMatches(s133, s134, true, StdFileTypes.JAVA);
    assertEquals(
      "find sym finds declaration",
      4, results.size()
    );

    assertEquals(
      "find sym finds declaration",
      2, findMatchesCount(s133_2, s134, true)
    );
    final String in = "class C {" +
                      "  {" +
                      "    int i = 0;" +
                      "    i += 1;" +
                      "    i = 3;" +
                      "    int j = i;" +
                      "    i();" +
                      "  }" +
                      "  void i() {}" +
                      "}";
    final String pattern1 = "'_:[read]";
    assertEquals("Find reads of symbol (including operator assignment)", 2, findMatchesCount(in, pattern1));

    final String pattern2 = "'_:[write && regex( i )]";
    assertEquals("Find writes of symbol", 3, findMatchesCount(in, pattern2));

    final String source = "class A {" +
                          "  static A a() {};" +
                          "  void m() {" +
                          "    A a = A.a();" +
                          "  }" +
                          "}";
    final String pattern3 = "A";
    assertEquals("No duplicate results", 4, findMatchesCount(source, pattern3));
  }

  public void testSearchGenerics() {
    assertEquals(
      "parameterized class match",
      findMatchesCount(s81,s82),
      2
    );

    assertEquals(
      "parameterized instanceof match",
      findMatchesCount(s81,s82_2),
      1
    );

    assertEquals(
      "parameterized cast match",
      findMatchesCount(s81,s82_3),
      1
    );

    assertEquals(
      "parameterized symbol without variables matching",
      findMatchesCount(s81, "S<T>"),
      2
    );

    assertEquals(
      "parameterized definition match",
      findMatchesCount(s81,s82_4),
      3
    );

    assertEquals(
      "parameterized method match",
      findMatchesCount(s81,s82_5),
      1
    );

    assertEquals(
      "parameterized constraint match",
      findMatchesCount(s81_2,s82_6),
      2
    );

    assertEquals(
      "symbol matches parameterization",
      findMatchesCount(s81,s82_7),
      29
    );

    assertEquals(
      "symbol matches parameterization 2",
      findMatchesCount(s81_2,s82_7),
      7
    );

    String s81_3 = " class A {\n" +
                   "  public static <T> Collection<T> unmodifiableCollection(int c) {\n" +
                   "    return new d<T>(c);\n" +
                   "  }\n" +
                   "  static class d<E> implements Collection<E>, Serializable {\n" +
                   "    public <T> T[] toArray(T[] a)       {return c.toArray(a);}\n" +
                   "  }\n" +
                   "}";
    assertEquals(
      "typed symbol symbol",
      findMatchesCount(s81_3,s82_5),
      2
    );

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
    String s82_8 = "'T<'_Subst+>";
    assertEquals(
      "typed symbol",
      8,
      findMatchesCount(s81_4,s82_8)
    );

    String s81_5 = "class A { HashMap<String, Integer> variable = new HashMap<String, Integer>(\"aaa\");}";
    String s82_9 = "'_Type<'_GType, '_GType2> '_instance = new '_Type<'_GType, '_GType2>('_Param);";
    assertEquals(
      "generic vars in new",
      findMatchesCount(s81_5,s82_9),
      1
    );
    assertEquals(
      "no exception on searching for diamond operator",
      findMatchesCount(s81_5, "new 'Type<>('_Param)"),
      0
    );
    assertEquals(
      "order of parameters matters",
      0,
      findMatchesCount(s81_5, "HashMap<Integer, String>")
    );
    assertEquals(
      "order of parameters matters 2",
      2,
      findMatchesCount(s81_5, "HashMap<String, Integer>")
    );

    String source1 = "class Comparator<T> { private Comparator<String> c; private Comparator d; private Comparator e; }";
    String target1 = "java.util.Comparator 'a;";
    assertEquals(
      "qualified type should not match 1",
      0,
      findMatchesCount(source1, target1)
    );

    String target2 = "java.util.Comparator<String> 'a;";
    assertEquals(
      "qualified type should not match 2",
      0,
      findMatchesCount(source1, target2)
    );

    assertEquals(
      "unparameterized type query should match",
      3,
      findMatchesCount(source1, "Comparator 'a;")
    );

    assertEquals(
      "parameterized type query should only match parameterized",
      1,
      findMatchesCount(source1, "Comparator<'_a> 'b;")
    );

    assertEquals(
      "should find unparameterized only",
      2,
      findMatchesCount(source1, "Comparator<'_a{0,0}> 'b;")
    );

    String source2 = "class A<@Q T> {}\n" +
                     "class B<T> {}";
    assertEquals(
      "find annotated type parameter",
      1,
      findMatchesCount(source2, "class $A$<@Q $T$> {}")
    );

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
    // searching for parameterized pattern
    assertEquals("search for parameterized pattern",findMatchesCount(s14_1,s15),2);

    assertEquals("search for parameterized pattern 2",findMatchesCount(s14_2,s15),5);

    options.setRecursiveSearch(false);

    assertEquals("search for parameterized pattern-non-recursive",findMatchesCount(s14_1,s15),1);

    assertEquals("search for parameterized pattern 2-non-recursive",findMatchesCount(s14_2,s15),2);

    // typed vars with arrays
    assertEquals("typed pattern with array 2-non-recursive",findMatchesCount(s23,s24_2),4);

    options.setRecursiveSearch(true);

      // searching for parameterized pattern
    assertEquals("search for parameterized pattern 3",findMatchesCount(s14_2,s16),1);

    // searching for parameterized pattern in complex expr (with field selection)
    assertEquals("search for parameterized pattern in field selection",findMatchesCount(s17,s18_1),1);

    // searching for parameterized pattern in complex expr (with method call)
    assertEquals("search for parameterized pattern with method call",findMatchesCount(s17,s18_2),1);

    // searching for parameterized pattern in complex expr (with method call)
    assertEquals("search for parameterized pattern with method call ep.2",findMatchesCount(s17,s18_3),4);

    // searching for parameterized pattern in definition with initializer
    assertEquals("search for same var constraint",findMatchesCount(s19,s20),1);

    // searching for semi anonymous parameterized pattern in definition with initializer
    assertEquals("search for same var constraint for semi anonymous typed vars",findMatchesCount(s19,s20_2),1);

    // support for type var constraint
    assertEquals("search for typed var constraint",findMatchesCount(s22,s21_1),1);

    // noncompatible same typed var constraints
    try {
      findMatchesCount(s22,s21_2);
      assertFalse("search for noncompatible typed var constraint",false);
    } catch(MalformedPatternException e) {
    }

      // compatible same typed var constraints
    assertEquals("search for same typed var constraint",findMatchesCount(s22,s21_3),1);

    // typed var with instanceof
    assertEquals("typed instanceof",findMatchesCount(s65,s66),1);

    try {
      // warn on incomplete instanceof
      findMatchesCount(s65, "'_T instanceof");
      fail();
    } catch (MalformedPatternException e) {
      assertEquals("Type expected", e.getMessage());
    }

    // typed vars with arrays
    assertEquals("typed pattern with array",findMatchesCount(s23,s24_1),2);

    // typed vars with arrays
    assertEquals("typed pattern with array 2",findMatchesCount(s23,s24_2),6);

    // typed vars in class name, method name, its return type, parameter type and name
    assertEquals("typed pattern in class name, method name, return type, parameter type and name",findMatchesCount(s25,s26),1);

    assertEquals(
      "finding interface",
      findMatchesCount(s27,s28),
      1
    );

    // finding anonymous type vars
    assertEquals(
      "anonymous typed vars",
      findMatchesCount(s29,s30),
      1
    );

    // finding descedants
    assertEquals(
      "finding class descendants",
      findMatchesCount(s31,s32),
      2
    );

    // finding interface implementation
    assertEquals(
      "interface implementation",
      findMatchesCount(s33,s34),
      2
    );

    // different order of fields and methods
    assertEquals(
      "different order of fields and methods",
      findMatchesCount(s35,s36),
      1
    );

    // different order of exceptions in throws
    assertEquals(
      "differend order in throws",
      findMatchesCount(s37,s38),
      1
    );

    // class pattern without extends matches pattern with extends
    assertEquals(
      "match of class without extends to class with it",
      findMatchesCount(s39,s40),
      2
    );

    // class pattern without extends matches pattern with extends
    assertEquals(
      "match of class without extends to class with it, ep. 2",
      findMatchesCount(s41,s42_1),
      2
    );

    // class pattern without extends matches pattern with extends
    assertEquals(
      "match of class without extends to class with it, ep 3",
      4,
      findMatchesCount(s41,s42_2)
    );

    assertEquals("match class with fields without initializers", 2, findMatchesCount(s41, "class '_ { '_T '_T2 = '_T3{0,0}; } "));

    // typed reference element
    assertEquals(
      "typed reference element",
      findMatchesCount(s51,s52),
      2
    );

    // empty name of type var
    assertEquals(
      "empty name for typed var",
      findMatchesCount(s59,s60),
      1
    );

    // comparing method with constructor
    assertEquals(
      "comparing method with constructor",
      findMatchesCount(s63,s64),
      1
    );

    // comparing method with constructor
    assertEquals(
      "finding nested class",
      findMatchesCount(s63_2,s64),
      2
    );

    // comparing method with constructor
    assertEquals(
      "finded nested class by special pattern",
      findMatchesCount(s63_2,s64_2),
      1
    );

    assertEquals(
      "* regexp for typed var",
      findMatchesCount(s61,s62_1),
      5
    );

    assertEquals(
      "+ regexp for typed var",
      findMatchesCount(s61,s62_2),
      4
    );

    assertEquals(
      "? regexp for typed var",
      findMatchesCount(s61,s62_3),
      2
    );

    assertEquals(
      "cast in method parameters",
      findMatchesCount(s67,s68),
      1
    );

    assertEquals(
      "searching for static field in static call",
      2,
      findMatchesCount(s69,s70)
    );

    assertEquals(
      "searching for static field in static call, 2",
      2,
      findMatchesCount(s69,s70_2)
    );

    assertEquals(
      "* regexp for anonymous typed var",
      findMatchesCount(s61,s62_4),
      3
    );

    assertEquals(
      "+ regexp for anonymous typed var",
      findMatchesCount(s61,s62_5),
      2
    );

    assertEquals(
      "? regexp for anonymous typed var",
      findMatchesCount(s61,s62_6),
      2
    );

    assertEquals(
      "statement inside anonymous class",
      findMatchesCount(s71,s72),
      3
    );

    assertEquals(
      "clever regexp match",
      findMatchesCount(s91,s92),
      2
    );

    assertEquals(
      "clever regexp match 2",
      findMatchesCount(s91,s92_2),
      2
    );

    assertEquals(
      "clever regexp match 3",
      findMatchesCount(s91,s92_3),
      2
    );
  }

  public void testSearchJavaDoc() {
    // javadoc comment in class
    assertEquals(
      "java doc comment in class",
      1,
      findMatchesCount(s57,s58)
    );

    assertEquals(
      "java doc comment in class in file",
      1,
      findMatchesCount(s57_2,s58,true)
    );

    // javadoc comment for field
    assertEquals(
      "javadoc comment for field",
      2,
      findMatchesCount(s57, s58_2)
    );

    // javadoc comment for method
    assertEquals(
      "javadoc comment for method",
      2,
      findMatchesCount(s57, s58_3)
    );

    // just javadoc comment search
    assertEquals(
      "just javadoc comment search",
      4,
      findMatchesCount(s57,s58_4)
    );

    assertEquals(
    "XDoclet metadata",
      2,
      findMatchesCount(s83,s84)
    );

    assertEquals(
    "XDoclet metadata 2",
      1,
      findMatchesCount(s83,s84_2)
    );

    assertEquals(
      "optional tag value match",
      6,
      findMatchesCount(s57, s58_5)
    );

    assertEquals(
      "multiple tags match +",
      2,
      findMatchesCount(s75,s76)
    );

    assertEquals(
      "multiple tags match *",
      3,
      findMatchesCount(s75, s76_2)
    );

    assertEquals(
      "multiple tags match ?",
      3,
      findMatchesCount(s75, s76_3)
    );

    assertEquals("no infinite loop on javadoc matching", 1, findMatchesCount(s57, "/** 'Text */ class '_ { }"));
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

    assertEquals(
      "serialization match",
      findMatchesCount(s133,s134),
      2
    );

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

    assertEquals(
      "Struts actions",
      findMatchesCount(s135,s136),
      2
    );

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

    assertEquals(
      "singleton search",
      findMatchesCount(s123,s124),
      1
    );

    String s1111 = "if (true) { a=1; b=1; } else { a=1; }\n" +
                   "if(true) { a=1; } else { a=1; b=1; }\n" +
                   "if(true) { a=1; b=2; } else { a = 1; b=2; }";
    String s1112 = "if (true) { '_a{1,2}; } else { '_a; }";

    assertEquals(
      "same multiple name pattern",
      findMatchesCount(s1111,s1112),
      1
    );
  }

  public void testHierarchy() {
    final String s105 = "class B {} class A extends B { }";
    final String s106 = "class '_ extends '_:[ref('T)] {}";
    assertEquals(
      "extends match",
      findMatchesCount(s105,s106),
      1
    );

    final String s107 = "interface IA {} interface IB extends IA { } interface IC extends IB {} interface ID extends IC {}" +
                        "class A implements IA {} class B extends A { } class C extends B implements IC {} class D extends C {}";
    final String s108 = "class '_ extends 'Type:+A {}";
    final String s108_2 = "class '_ implements 'Type:+IA {}";

    assertEquals(
      "extends navigation match",
      findMatchesCount(s107,s108),
      2
    );

    assertEquals(
      "implements navigation match",
      3,
      findMatchesCount(s107,s108_2)
    );

    final String s109 = "interface I {} interface I2 extends I {} class A implements I2 {} class B extends A { } class C extends B {} class D { void e() { C c; B b; A a;} }";
    final String s110 = "'_:*A '_;";
    final String s110_2 = "'_:*I '_;";
    final String s110_3 = "'_:*[regex( I ) && ref('T)] '_;";
    final String s110_4 = "'_:*[regex( I ) && ref2('T)] '_;";
    assertEquals(
      "extends navigation match in definition",
      findMatchesCount(s109,s110),
      3
    );

    assertEquals(
      "implements navigation match in definition 2",
      findMatchesCount(s109,s110_2),
      3
    );

    assertEquals(
      "implements navigation match in definition 2 with nested conditions",
      findMatchesCount(s109,s110_3),
      1
    );

    try {
      findMatchesCount(s109,s110_4);
      assertFalse("implements navigation match in definition 2 with nested conditions - incorrect cond",false);
    } catch(UnsupportedPatternException ex) {}

    final String s111 = "interface E {} class A implements E {} class B extends A { int f = 0; } class C extends B {} class D { void e() { C c; B b; A a;} }";
    final String s112 = "'_";
    assertEquals(
      "symbol match",
      findMatchesCount(s111,s112),
      17
    );

    final String s113 = "class B {int c; void d() {} } int a; B b; a = 1; b.d(); ++a; int c=a; System.out.println(a); " +
                        "b.c = 1; System.out.println(b.c); b.c++;";
    final String s114 = "'_:[read]";
    final String s114_2 = "'_:[write]";
    assertEquals(
      "read symbol match",
      findMatchesCount(s113,s114),
      11
    );

    assertEquals(
      "write symbol match",
      findMatchesCount(s113,s114_2),
      5
    );

    final String s115 = "class B {} public class C {}";
    final String s116 = "public class '_ {}";
    assertEquals(
      "public modifier for class",
      findMatchesCount(s115,s116),
      1
    );

    final String s117 = "class A { int b; void c() { int e; b=1; this.b=1; e=5; " +
                        "System.out.println(e); " +
                        "System.out.println(b); System.out.println(this.b);} }";
    final String s118 = "this.'Field";
    final String s118_2 = "this.'Field:[read]";
    final String s118_3 = "this.'Field:[write]";

    assertEquals(
      "fields of class",
      4,
      findMatchesCount(s117,s118)
    );

    assertEquals(
      "fields of class read",
      2,
      findMatchesCount(s117,s118_2)
    );

    assertEquals(
      "fields of class written",
      2,
      findMatchesCount(s117,s118_3)
    );

    final String s119 = "try { a.b(); } catch(IOException e) { c(); } catch(Exception ex) { d(); }";
    final String s120 = "try { '_; } catch('_ '_) { '_; }";
    final String s120_2 = "try { '_; } catch(Throwable '_) { '_; }";
    assertEquals(
      "catches loose matching",
      findMatchesCount(s119,s120),
      1
    );

    assertEquals(
      "catches loose matching 2",
      findMatchesCount(s119,s120_2),
      0
    );

    final String s121 = "class A { private int a; class Inner {} } " +
                        "class B extends A { private int a; class Inner2 {} }";
    final String s122 = "class '_ { int '_:* ; }";
    final String s122_2 = "class '_ { int '_:+hashCode (); }";
    final String s122_3 = "class '_ { class '_:* {} }";
    assertEquals(
      "hierarchical matching",
      findMatchesCount(s121,s122),
      2
    );

    assertEquals(
      "hierarchical matching 2",
      findMatchesCount(s121,s122_2),
      4
    );

    assertEquals(
      "hierarchical matching 3",
      findMatchesCount(s121,s122_3),
      2
    );
  }

  public void testSearchInCommentsAndLiterals() {
    String s1 = "{" +
                "// This is some comment\n" +
                "/* This is another\n comment*/\n" +
                "// Some garbage\n"+
                "/** And now third comment*/\n" +
                "/** Some garbage*/ }";
    String s2 = "// 'Comment:[regex( .*(?:comment).* )]";
    String s3 = "/** 'Comment:[regex( .*(?:comment).* )] */";
    String s2_2 = "/* 'Comment:[regex( .*(?:comment).* )] */";

    assertEquals(
      "Comment matching",
      findMatchesCount(s1,s2),
      3
    );

    assertEquals(
      "Comment matching, 2",
      3,
      findMatchesCount(s1,s2_2)
    );

    assertEquals(
      "Java doc matching",
      findMatchesCount(s1,s3),
      1
    );

    String s4 = "\"'test\", \"another test\", \"garbage\"";
    String s5 = "\"'test:[regex( .*test.* )]\"";
    String s6 = "\"''test\"";

    assertEquals(
      "Literal content",
      findMatchesCount(s4,s5),
      2
    );

    assertEquals(
      "Literal content with escaping",
      findMatchesCount(s4,s6),
      1
    );

    String s7 = "\"aaa\"";
    String s8 = "\"'test:[regex( aaa )]\"";

    assertEquals(
      "Simple literal content",
      findMatchesCount(s7,s8),
      1
    );

    String s9 = "\" aaa \" \" bbb \" \" ccc ccc aaa\"";
    String s10 = "\"'test:[regexw( aaa|ccc )]\"";
    String s11 = "\"'test:[regexw( bbb )]\"";

    assertEquals(
      "Whole word literal content with alternations",
      findMatchesCount(s9,s10),
      2
    );

    assertEquals(
      "Whole word literal content",
      findMatchesCount(s9,s11),
      1
    );

    String s12 = "assert agentInfo != null : \"agentInfo is null\";\n" +
                 "assert addresses != null : \"addresses is null\";";
    String s13 = "assert $exp$ != null : \"$exp$ is null\";";

    assertEquals(
      "reference to substitution in comment",
      findMatchesCount(s12,s13),
      2
    );

    String s14 = "\"(some text with special chars)\"," +
                 "\" some\"," +
                 "\"(some)\"";
    String s15 = "\"('a:[regexw( some )])\"";

    assertEquals(
      "meta char in literal",
      2,
      findMatchesCount(s14,s15)
    );

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
    assertEquals(
      "complete comment match",
      1,
      findMatchesCount(s16,s17,true)
    );

    String s18 = "public class A {\n" +
                 "   private void f(int i) {\n" +
                 "       int g=0; //sss\n" +
                 "   }\n" +
                 "}";
    String s19 = "class $c$ {\n" +
                 "   $type$ $f$($t$ $p$){\n" +
                 "       $s$; // sss\n" +
                 "   }\n" +
                 "}";
    assertEquals(
      "statement match with comment",
      1,
      findMatchesCount(s18,s19)
    );
  }

  public void testOther() {
    assertEquals(
      "optional init match in definition",
      findMatchesCount(s73,s74),
      4
    );

    assertEquals(
      "null match",
      findMatchesCount(s77,s78),
      0
    );

    assertEquals(
      "body of method by block search",
      findMatchesCount(s79,s80),
      2
    );


    assertEquals(
      "first matches, next not",
      findMatchesCount(s95,s96),
      2
    );

    final String s97 = "class A { int c; void b() { C d; } } class C { C() { A a; a.b(); a.c=1; } }";
    final String s98 = "'_.'_:[ref('T)] ()";
    final String s98_2 = "'_.'_:[ref('T)]";
    final String s98_3 = "'_:[ref('T)].'_ ();";
    final String s98_4 = "'_:[ref('T)] '_;";

    assertEquals(
      "method predicate match",
      findMatchesCount(s97,s98),
      1
    );

    assertEquals(
      "field predicate match",
      findMatchesCount(s97,s98_2),
      1
    );

    assertEquals(
      "dcl predicate match",
      findMatchesCount(s97,s98_3),
      1
    );

    final String s99 = " char s = '\\u1111';  char s1 = '\\n'; ";
    final String s100 = " char 'var = '\\u1111'; ";
    final String s100_2 = " char 'var = '\\n'; ";
    assertEquals(
      "char constants in pattern",
      findMatchesCount(s99,s100),
      1
    );

    assertEquals(
      "char constants in pattern 2",
      findMatchesCount(s99,s100_2),
      1
    );

    assertEquals(
      "class predicate match (from definition)",
      findMatchesCount(s97,s98_4),
      3
    );

    final String s125 = "a=1;";
    final String s126 = "'t:[regex(a)]";

    try {
      findMatchesCount(s125,s126);
      assertFalse("spaces around reg exp check",false);
    } catch(MalformedPatternException ex) {}

    final String s101 = "class A { void b() { String d; String e; String[] f; f.length=1; f.length=1; } }";
    final String s102 = "'_:[ref('T)] '_;";

    assertEquals(
      "distinct match",
      findMatchesCount(s101,s102),
      1
    );

    final String s103 = " a=1; ";
    final String s104 = "'T:{ ;";
    try {
      findMatchesCount(s103,s104);
      assertFalse("incorrect reg exp",false);
    } catch(MalformedPatternException ex) {
    }

    final String s106 = "$_ReturnType$ $MethodName$($_ParameterType$ $_Parameter$);";
    final String s105 = " aaa; ";

    try {
      findMatchesCount(s105,s106);
      assertFalse("incorrect reg exp 2",false);
    } catch(UnsupportedPatternException ex) {
    }

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

    String s109 = "class A { void b(); int b(int c); char d(char e); }\n" +
                  "A a; a.b(1); a.b(2); a.b(); a.d('e'); a.d('f'); a.d('g');";
    String s110 = "'_a.'_b:[exprtype( int ) ]('_c*);";
    assertEquals("caring about method return type", 2, findMatchesCount(s109,s110));

    String s111 = "class A { void getManager() { getManager(); } };\n" +
                  "class B { void getManager() { getManager(); getManager(); } };";
    String s112 = "'Instance?:[exprtype( B )].getManager()";
    assertEquals("caring about missing qualifier type", 2, findMatchesCount(s111,s112));

    String s112a = "'Instance?:[regex( B )].getManager()";
    assertEquals("static query should not match instance method", 0, findMatchesCount(s111, s112a));

    String s112b = "B.getManager()";
    assertEquals("static query should not match instance method 2", 0, findMatchesCount(s111, s112b));

    String s113 = "class A { static void a() { a(); }}\n" +
                  "class B { static void a() { a(); a(); }}\n";
    String s114 = "'_Q?:[regex( B )].a()";
    assertEquals("should care about implicit class qualifier", 2, findMatchesCount(s113, s114));

    String s114a = "B.a()";
    assertEquals("should match simple implicit class qualifier query", 2, findMatchesCount(s113, s114a));

    String s114b = "'_Q?:[exprtype( B )].a()";
    assertEquals("instance query should not match static method", 0, findMatchesCount(s113, s114b));

    String s115 = "class A { int a; int f() { return a; }}\n" +
                  "class B { int a; int g() { return a + a; }}\n";
    String s116 = "'_Instance?:[exprtype( B )].a";
    assertEquals("should care about implicit instance qualifier", 2, findMatchesCount(s115, s116));

    String s116a = "A.a";
    assertEquals("should not match instance method", 0, findMatchesCount(s115, s116a));

    String s117 = "class A { static int a; static int f() { return a; }}\n" +
                  "class B { static int a; static int g() { return a + a; }}\n";
    String s118 = "'_Q?:[regex( B )].a";
    assertEquals("should care about implicit class qualifier for field", 2, findMatchesCount(s117, s118));

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
    String s1_2 = "import java.util.List;\n" +
                  "class A { List l; }";
    String s2 = "class '_ { 'Type:java\\.util\\.List '_Field; }";

    assertEquals("No matches for qualified class",findMatchesCount(s1,s2,true),0);
    assertEquals("Matches for qualified class",findMatchesCount(s1_2,s2,true),1);

    String s3 = "import java.util.ArrayList;\n" +
                "class A { ArrayList l; }";
    String s4 = "class '_ { 'Type:*java\\.util\\.Collection '_Field; }";
    assertEquals("Matches for qualified class in hierarchy",findMatchesCount(s3,s4,true),1);

    String s5 = "import java.util.List;\n" +
                "class A { { List l = new List(); l.add(\"1\"); }  }";
    String s5_2 = "import java.awt.List;\n" +
                  "class A { { List l = new List(); l.add(\"1\"); } }";
    String s6 = "'a:[exprtype( java\\.util\\.List )]";
    String s6_2 = "'a:[exprtype( *java\\.util\\.Collection )]";
    String s6_3 = "java.util.List '_a = '_b?;";

    assertEquals("Matches for qualified expr type",findMatchesCount(s5,s6,true), 2);
    assertEquals("No matches for qualified expr type",findMatchesCount(s5_2,s6,true),0);
    assertEquals("Matches for qualified expr type in hierarchy",findMatchesCount(s5,s6_2,true), 2);

    assertEquals("Matches for qualified var type in pattern",findMatchesCount(s5,s6_3,true),1);
    assertEquals("No matches for qualified var type in pattern",findMatchesCount(s5_2,s6_3,true),0);

    String s7 = "import java.util.List;\n" +
                "class A extends List { }";
    String s7_2 = "import java.awt.List;\n" +
                  "class A extends List {}";

    String s8 = "class 'a extends java.util.List {}";

    assertEquals("Matches for qualified type in pattern",findMatchesCount(s7,s8,true),1);
    assertEquals("No matches for qualified type in pattern",findMatchesCount(s7_2,s8,true),0);

    String s9 = "String.intern(\"1\");\n" +
                "java.util.Collections.sort(null);" +
                "java.util.Collections.sort(null);";
    String s10 = "java.lang.String.'_method ( '_params* )";
    assertEquals("FQN in class name",1,findMatchesCount(s9,s10,false));
  }

  public void testAnnotations() throws Exception {
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
                "public class $a$ {}\n";

    assertEquals("Simple find annotated class",2,findMatchesCount(s1,s2,false));
    assertEquals("Match value of anonymous name value pair 1", 1, findMatchesCount(s1, "@MyBean(\"a\") class $a$ {}"));
    assertEquals("Match value of anonymous name value pair 2", 2, findMatchesCount(s1, "@MyBean(\"\") class $a$ {}"));

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
    String s4_2 = "class '_a {\n" +
                  "  @'_Annotation+ ()\n" +
                  "  String 'field* ;\n" +
                  "}";

    assertEquals("Find annotation members of annotated field class",4,findMatchesCount(s3,s4,false));
    assertEquals("Find annotation fields",3,findMatchesCount(s3,s4_2,false));

    String s5 = "class A {" +
                "  @NotNull private static Collection<PsiElement> resolveElements(final PsiReference reference, final Project project) {}\n" +
                "  @NotNull private static Collection resolveElements2(final PsiReference reference, final Project project) {}\n" +
                "}";
    String s6 = "class '_c {@NotNull '_rt 'method* ('_pt* '_p*){ '_inst*; } }";
    String s6_2 = "class '_c {@'_:NotNull '_rt 'method* ('_pt* '_p*){ '_inst*; } }";

    assertEquals("Find annotated methods",2,findMatchesCount(s5,s6));
    assertEquals("Find annotated methods, 2",2,findMatchesCount(s5,s6_2));

    String s7 = "class A { void message(@NonNls String msg); }\n" +
                "class B { void message2(String msg); }\n" +
                "class C { void message2(String msg); }";
    String s8 = "class '_A { void 'b( @'_Ann{0,0}:NonNls String  '_); }";
    assertEquals("Find not annotated methods",2,findMatchesCount(s7,s8));

    String s9 = "class A {\n" +
                "  Object[] method1() {}\n" +
                "  Object method1_2() {}\n" +
                "  Object method1_3() {}\n" +
                "  Object method1_4() {}\n" +
                "  @MyAnnotation Object[] method2(int a) {}\n" +
                "  @NonNls Object[] method3() {}\n" +
                "}";
    String s10 = "class '_A { @'_Ann{0,0}:NonNls '_Type:Object\\[\\] 'b+( '_pt* '_p* ); }";
    String s10_2 = "class '_A { @'_Ann{0,0}:NonNls '_Type [] 'b+( '_pt* '_p* ); }";
    String s10_3 = "class '_A { @'_Ann{0,0}:NonNls '_Type:Object [] 'b+( '_pt* '_p* ); }";
    assertEquals("Find not annotated methods, 2",2,findMatchesCount(s9,s10));
    assertEquals("Find not annotated methods, 2",2,findMatchesCount(s9,s10_2));
    assertEquals("Find not annotated methods, 2",2,findMatchesCount(s9,s10_3));

    String s11 = "class A {\n" +
                 "@Foo(value=baz) int a;\n" +
                 "@Foo(value=baz2) int a2;\n" +
                 "@Foo(value=baz2) int a3;\n" +
                 "@Foo(value2=baz3) int a3;\n" +
                 "@Foo(value2=baz3) int a3;\n" +
                 "@Foo(value2=baz3) int a3;\n" +
                 "@Foo(value2=baz4) int a3;\n" +
                 "}";
    String s12 = "@Foo(value=baz) int 'a;)";
    String s12_2 = "@Foo(value='baz:baz2 ) int '_a;)";
    String s12_3 = "@Foo('value:value2 = baz3 ) int '_a;)";
    String s12_4 = "@Foo('value:value2 = '_baz3:baz3 ) int '_a;)";
    String s12_5 = "@Foo('value:value2 = '_baz3:baz ) int '_a;)";
    String s12_6 = "@Foo('value:value2 = '_baz3 ) int '_a;)";
    String s12_7 = "@Foo('value:value2 = ) int '_a;";

    assertEquals("Find anno parameter value",1,findMatchesCount(s11,s12));
    assertEquals("Find anno parameter value",2,findMatchesCount(s11,s12_2));
    assertEquals("Find anno parameter value",3,findMatchesCount(s11,s12_3));
    assertEquals("Find anno parameter value",3,findMatchesCount(s11,s12_4));
    assertEquals("Find anno parameter value",0,findMatchesCount(s11,s12_5));
    assertEquals("Find anno parameter value",4,findMatchesCount(s11,s12_6));
    assertEquals("Find anno parameter value",4,findMatchesCount(s11,s12_7));

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
    assertEquals("Find annotation on package statement", 1, findMatchesCount(source4, "@'_Annotation", true));

    final String source5 ="class A {" +
                          "  boolean a(Object o) {" +
                          "    return o instanceof @HH String;" +
                          "  }" +
                          "}";
    assertEquals("Find annotation on instanceof expression", 1, findMatchesCount(source5, "'_a instanceof @HH String"));
    assertEquals("Match annotation correctly on instanceof expression", 0, findMatchesCount(source5, "'_a instanceof @GG String"));
  }

  public void testBoxingAndUnboxing() {
    String s1 = " class A { void b(Integer i); void b2(int i); void c(int d); void c2(Integer d); }\n" +
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
                "int j = Integer.valueOf(4);\n";
    String s2 = "a.'b('_Params:[formal( Integer ) && exprtype( int ) ])";
    String s2_2 = "a.c('_Params:[formal( int ) && exprtype( Integer ) ])";

    assertEquals("Find boxing in method call",1,findMatchesCount(s1,s2,false));
    assertEquals("Find unboxing in method call",2,findMatchesCount(s1,s2_2,false));

    String pattern1 = "'_a:[formal( Integer ) && exprtype( int ) ]";
    assertEquals("Find any boxing", 2, findMatchesCount(s1, pattern1));

    String pattern2 = "'_a:[formal( int ) && exprtype( Integer ) ]";
    assertEquals("Find any unboxing", 3, findMatchesCount(s1, pattern2));
  }

  public void testCommentsInDclSearch() {
    String s1 = "class A {\n" +
                "  int a; // comment\n" +
                "  char b;\n" +
                "  int c; // comment2\n" +
                "}";
    String s1_2 = "class A {\n" +
                  "  // comment\n" +
                  "  int a;\n" +
                  "  char b;\n" +
                  "  // comment2\n" +
                  "  int c;\n" +
                  "}";

    String s2 = "'_Type '_Variable = '_Value?; //'Comment";
    String s2_2 = "//'Comment\n" +
                  "'_Type '_Variable = '_Value?;";

    assertEquals("Find field by dcl with comment",2,findMatchesCount(s1,s2));
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
    String s2 = "@Modifier(\"packageLocal\") '_Type '_Variable = '_Value?;";
    String s2_2 = "@Modifier({\"packageLocal\",\"private\"}) '_Type '_Variable = '_Value?;";
    String s2_3 = "@Modifier({\"PackageLocal\",\"private\"}) '_Type '_Variable = '_Value?;";

    assertEquals("Finding package-private dcls",1,findMatchesCount(s1,s2));
    assertEquals("Finding package-private dcls",3,findMatchesCount(s1,s2_2));

    try {
      findMatchesCount(s1,s2_3);
      assertTrue("Finding package-private dcls",false);
    } catch(MalformedPatternException ex) {

    }

    String s3 = "class A {\n" +
                "  int a;\n" +
                "  static char b;\n" +
                "  static char b2;\n" +
                "}";
    String s4 = "@Modifier(\"Instance\") '_Type '_Variable = '_Value?;";
    String s4_2 = "@Modifier({\"static\",\"Instance\"}) '_Type '_Variable = '_Value?;";
    assertEquals("Finding instance fields",1,findMatchesCount(s3,s4));
    assertEquals("Finding all fields",3,findMatchesCount(s3,s4_2));

    String s5 = "class A {}\n" +
                "abstract class B {}\n" +
                "final class C {}\n" +
                "class D {}";
    String s6 = "@Modifier(\"Instance\") class 'Type {}";
    String s6_2 = "@Modifier({\"abstract\",\"final\",\"Instance\"}) class 'Type {}";
    assertEquals("Finding instance classes",3,findMatchesCount(s5,s6));
    assertEquals("Finding all classes",4,findMatchesCount(s5,s6_2));
  }

  public void testSearchTransientFieldsWithModifier() {
    String source =
      "public class TestClass {\n" +
      "  transient private String field1;\n" +
      "  transient String field2;\n" +
      "  String field3;\n" +
      "}";

    String template = "transient @Modifier(\"packageLocal\") '_Type '_Variable = '_Value?;";

    assertEquals("Finding package-private transient fields", 1, findMatchesCount(source, template));
  }

  public void test() {
    String s1 = "if (LOG.isDebugEnabled()) {\n" +
                "  int a = 1;\n" +
                "  int a = 1;\n" +
                "}";
    String s2 = "if ('_Log.isDebugEnabled()) {\n" +
                "  '_ThenStatement;\n" +
                "  '_ThenStatement;\n" +
                "}";
    assertEquals("Comparing declarations",1,findMatchesCount(s1,s2));
  }

  public void testFindStaticMethodsWithinHierarchy() {
    String s1 = "class A {}\n" +
                "class B extends A { static void foo(); }\n" +
                "class B2 extends A { static void foo(int a); }\n" +
                "class B3 extends A { static void foo(int a, int b); }\n" +
                "class C { static void foo(); }\n" +
                "B.foo();\n" +
                "B2.foo(1);\n" +
                "B3.foo(2,3);\n" +
                "C.foo();";
    String s2 = "'_Instance:[regex( *A )].'_Method:[regex( foo )] ( '_Params* )";
    assertEquals("Find static methods within expr type hierarchy", 3, findMatchesCount(s1,s2));
  }

  public void testFindClassesWithinHierarchy() {
    String s1 = "class A implements I {}\n" +
                "interface I {}\n" + 
                "class B extends A implements I { }\n" +
                "class B2 implements I  { }\n" +
                "class B3 extends A { }\n" +
                "class C extends B2 { static void foo(); }\n";
    String s2 = "class '_ extends '_Extends:[!regex( *A )] implements '_Implements:[regex( I )] {}";
    String s2_2 = "class '_ extends '_Extends:[!regex( *A )]{}";
    assertEquals("Find class within type hierarchy with not", 1, findMatchesCount(s1,s2));
    assertEquals("Find class within type hierarchy with not, 2", 1, findMatchesCount(s1,s2_2));
  }

  public void testFindTryWithoutProperFinally() {
    String s1 = "try {\n" +
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
                "}";
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

  public void _testStaticInstanceInitializers() {
    String s1 = "public class DiallingNumber {\n static { int a = 1; } static { int b = 1; } { int c = 2; }}";
    String s2 = "class '_Class {\n" + "    static { 't*; } }";
    String s2_2 = "class '_Class {\n" + "    { 't*; } }";
    String s2_3 = "class '_Class {\n" + "    @Modifier(\"Instance\") { 't*; } }";
    assertEquals("Static / instance initializers", 2, findMatchesCount(s1,s2));
    assertEquals("Static / instance initializers", 1, findMatchesCount(s1,s2_3));
    assertEquals("Static / instance initializers", 3, findMatchesCount(s1,s2_2));
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
    String s2 = "try  { '_st*; } catch('_Type 't+) { '_st2*; }";

    final List<PsiVariable> vars = new ArrayList<>();
    final PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("_.java", s1);

    file.acceptChildren(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitVariable(final PsiVariable variable) {
        super.visitVariable(variable);
        vars.add(variable);
      }
    });

    assertEquals(7, vars.size());
    List<MatchResult> results = new ArrayList<>();

    Matcher testMatcher = new Matcher(getProject());
    MatchOptions options = new MatchOptions();
    options.setSearchPattern(s2);
    MatcherImplUtil.transform(options);
    options.setFileType(StdFileTypes.JAVA);

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
    String s2_2 = "try  { '_st*; } catch('Type:Type2 '_t) { '_st2*; }";

    options.clearVariableConstraints();
    options.setSearchPattern(s2_2);
    MatcherImplUtil.transform(options);

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
    String s2 = "{\n" +
                "  '_a*:[contains( \"'type $a$ = $b$;\" )];\n" +
                "}";

    String s2_2 = "{\n" +
                "  '_a*:[!contains( \"$type$ $a$ = $b$;\" )];\n" +
                "}";

    assertEquals(2, findMatchesCount(s1, s2));
    assertEquals(1, findMatchesCount(s1, s2_2));
  }

  public void testWithinPredicate() {
    String s1 = "if (true) {\n" +
                "  int a = 1;\n" +
                "}\n" +
                "if (true) {\n" +
                "  int b = 1;\n" +
                "}\n" +
                "while(true) {\n" +
                "  int c = 2;\n" +
                "}";
    String s2 = "[within( \"if ('_a) { '_st*; }\" )]'_type 'a = '_b;";
    String s2_2 = "[!within( \"if ('_a) { '_st*; }\" )]'_type 'a = '_b;";

    assertEquals(2,findMatchesCount(s1, s2));
    assertEquals(1,findMatchesCount(s1, s2_2));

    String s3 = "if (true) {\n" +
                "  if (true) return;\n" +
                "  int a = 1;\n" +
                "}\n" +
                "else if (true) {\n" +
                "  int b = 2;\n" +
                "  return;\n" +
                "}\n" +
                "int c = 3;\n";
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

  public void testMultiStatementPatternWithTypedVariable() throws Exception {
    String s = "Integer i;\ni.valueOf();";
    String s_2 = "Integer i;\nint a = 1;\ni.valueOf();";
    String s2 = "Integer '_i;\n'_i.valueOf();";
    String s2_2 = "Integer '_i;\n'_st; '_i.valueOf();";
    String s2_3 = "Integer '_i;\n'_st*; '_i.valueOf();";
    
    assertEquals(1, findMatchesCount(s,s2));
    assertEquals(1, findMatchesCount(s_2,s2_2));
    assertEquals(1, findMatchesCount(s_2,s2_3));
    assertEquals(1, findMatchesCount(s,s2_3));
  }
  
  public void testFindAnnotationDeclarations() throws Exception {
    String s = "interface Foo {} interface Bar {} @interface X {}";
    String s2 = "@interface 'x {}";
        
    assertEquals(1, findMatchesCount(s,s2));
  }
  
  public void testFindEnums() throws Exception {
    String s = "class Foo {} class Bar {} enum X {}";
    String s2 = "enum 'x {}";
        
    assertEquals(1, findMatchesCount(s,s2));
  }

  public void testFindDeclaration() throws Exception {
    String s = "public class F {\n" +
               "  static Category cat = Category.getInstance(F.class.getName());\n" +
               "  Category cat2 = Category.getInstance(F.class.getName());\n" +
               "  Category cat3 = Category.getInstance(F.class.getName());\n" +
               "}";
    String s2 = "static $Category$ $cat$ = $Category$.getInstance($Arg$);";

    assertEquals(1, findMatchesCount(s,s2));
  }

  public void testFindMethodCallWithTwoOrThreeParameters() {
    String source = "{ String.format(\"\"); String.format(\"\", 1); String.format(\"\", 1, 2); String.format(\"\", 1, 2, 3); }";
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
                    //"}" +
                    //"class B {" +
                    //"  B(String t) {" +
                    //"    super.s = s;" + // would be nice if found also
                    //"  }" +
                    "}";

    String pattern = "'_var='_var";
    assertEquals(3, findMatchesCount(source, pattern));
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

    String pattern1 = "/*$A$a*/";
    MalformedPatternException ex = null;
    try {
      findMatchesCount(source, pattern1);
    } catch (MalformedPatternException e) {
      ex = e;
    }
    assertNotNull(ex);

    String pattern2 = "class $A$Visitor {}";
    try {
      findMatchesCount(source, pattern2);
    } catch (MalformedPatternException e) {
      ex = e;
    }
    assertNotNull(ex);

    String pattern3 = "class $Class$ { \n" +
                      "  class $n$$FieldType$ $FieldName$ = $Init$;\n" +
                      "}";
    try {
      findMatchesCount(source, pattern3);
    } catch (MalformedPatternException e) {
      ex = e;
    }
    assertNotNull(ex);
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
}
