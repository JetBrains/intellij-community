// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Maxim.Mossienko
 */
public class StructuralReplaceTest extends StructuralReplaceTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_16);
    final MatchOptions matchOptions = options.getMatchOptions();
    matchOptions.setFileType(JavaFileType.INSTANCE);
  }

  public void testReplaceInLiterals() {
    String s1 = "String ID_SPEED = \"Speed\";";
    String s2 = "String 'name = \"'_string\";";
    String s2_2 = "String 'name = \"'_string:[regex( .* )]\";";
    String s3 = "VSegAttribute $name$ = new VSegAttribute(\"$string$\");";
    String expectedResult = "VSegAttribute ID_SPEED = new VSegAttribute(\"Speed\");";

    assertEquals("Matching/replacing literals", expectedResult, replace(s1, s2, s3));
    assertEquals("Matching/replacing literals", expectedResult, replace(s1, s2_2, s3));

    String s4 = "params.put(\"BACKGROUND\", \"#7B528D\");";
    String s5 = "params.put(\"$FieldName$\", \"#$exp$\");";
    String s6 = "String $FieldName$ = \"$FieldName$\";\n" +
                "params.put($FieldName$, \"$exp$\");";
    String expectedResult2 = "String BACKGROUND = \"BACKGROUND\";\n" +
                             "params.put(BACKGROUND, \"7B528D\");";

    assertEquals("string literal replacement 2", expectedResult2, replace(s4, s5, s6));

    String s7 = """
      IconLoader.getIcon("/ant/property.png");
      IconLoader.getIcon("/ant/another/property.png");
      """;
    String s8 = "IconLoader.getIcon(\"/'_module/'_name:[regex( \\w+ )].png\");";
    String s9 = "Icons.$module$.$name$;";
    String expectedResult3 = """
      Icons.ant.property;
      IconLoader.getIcon("/ant/another/property.png");
      """;

    assertEquals("string literal replacement 3", expectedResult3, replace(s7, s8, s9));

    String s10 = """
      configureByFile(path + "1.html");
          checkResultByFile(path + "1_after.html");
          checkResultByFile(path + "1_after2.html");
          checkResultByFile(path + "1_after3.html");""";
    String s11 = "\"'a.html\"";
    String s12 = "\"$a$.\"+ext";
    String expectedResult4 = """
      configureByFile(path + "1."+ext);
          checkResultByFile(path + "1_after."+ext);
          checkResultByFile(path + "1_after2."+ext);
          checkResultByFile(path + "1_after3."+ext);""";

    assertEquals("string literal replacement 4", expectedResult4, replace(s10, s11, s12));
  }

  public void testReplace2() {
    String s1 = """
      package com.www.xxx.yyy;

      import javax.swing.*;

      public class Test {
        public static void main(String[] args) {
          if (1==1)
            JOptionPane.showMessageDialog(null, "MESSAGE");
        }
      }""";
    String s2 = "JOptionPane.'_showDialog(null, '_msg);";
    String s3 = "//FIXME provide a parent frame\n" +
                "JOptionPane.$showDialog$(null, $msg$);";

    String expectedResult = """
      package com.www.xxx.yyy;

      import javax.swing.*;

      public class Test {
        public static void main(String[] args) {
          if (1==1)
            //FIXME provide a parent frame
      JOptionPane.showMessageDialog(null, "MESSAGE");
        }
      }""";

    assertEquals("adding comment to statement inside the if body", expectedResult, replace(s1, s2, s3));

    String s4 = "myButton.setText(\"Ok\");";
    String s5 = "'_Instance.'_MethodCall:[regex( setText )]('_Parameter*:[regex( \"Ok\" )]);";
    String s6 = "$Instance$.$MethodCall$(\"OK\");";

    String expectedResult2 = "myButton.setText(\"OK\");";

    assertEquals("adding comment to statement inside the if body", expectedResult2, replace(s4, s5, s6));
  }

  public void testReplace() {
    String str = """
      // searching for several constructions
          lastTest = "several constructions match";
          matches = testMatcher.findMatches(s5,s4, options);
          if (matches==null || matches.size()!=3) return false;

          // searching for several constructions
          lastTest = "several constructions 2";
          matches = testMatcher.findMatches(s5,s6, options);
          if (matches.size()!=0) return false;

          //options.setLooseMatching(true);
          // searching for several constructions
          lastTest = "several constructions 3";
          matches = testMatcher.findMatches(s7,s8, options);
          if (matches.size()!=2) return false;""";

    String str2 = """
            lastTest = '_Descr;
            matches = testMatcher.findMatches('_In,'_Pattern, options);
            if (matches.size()!='_Number) return false;\
      """;
    String str3 = "assertEquals($Descr$,testMatcher.findMatches($In$,$Pattern$, options).size(),$Number$);";
    String expectedResult1 = """
      // searching for several constructions
      lastTest = "several constructions match";
      matches = testMatcher.findMatches(s5, s4, options);
      if (matches == null || matches.size() != 3) return false;

      // searching for several constructions
      assertEquals("several constructions 2", testMatcher.findMatches(s5, s6, options).size(), 0);

      //options.setLooseMatching(true);
      // searching for several constructions
      assertEquals("several constructions 3", testMatcher.findMatches(s7, s8, options).size(), 2);""";

    String str4 = "";

    options.setToReformatAccordingToStyle(true);
    assertEquals("Basic replacement with formatter", expectedResult1, replace(str, str2, str3));
    options.setToReformatAccordingToStyle(false);

    String expectedResult2 = """
      // searching for several constructions
          lastTest = "several constructions match";
          matches = testMatcher.findMatches(s5,s4, options);
          if (matches==null || matches.size()!=3) return false;

          // searching for several constructions

          //options.setLooseMatching(true);
          // searching for several constructions""";
    assertEquals("Empty replacement", expectedResult2, replace(str, str2, str4));

    String str5 = "testMatcher.findMatches('_In,'_Pattern, options).size()";
    String str6 = "findMatchesCount($In$,$Pattern$)";
    String expectedResult3 = """
      // searching for several constructions
      lastTest = "several constructions match";
      matches = testMatcher.findMatches(s5, s4, options);
      if (matches == null || matches.size() != 3) return false;

      // searching for several constructions
      assertEquals("several constructions 2", findMatchesCount(s5,s6), 0);

      //options.setLooseMatching(true);
      // searching for several constructions
      assertEquals("several constructions 3", findMatchesCount(s7,s8), 2);""";
    assertEquals("Expression replacement", expectedResult3, replace(expectedResult1, str5, str6));

    String str7 =
      "try { a.doSomething(); /*1*/b.doSomething(); } catch(IOException ex) {  ex.printStackTrace(); throw new RuntimeException(ex); }";
    String str8 = "try { '_Statements+; } catch('_ '_) { '_HandlerStatements+; }";
    String str9 = "$Statements$;";
    String expectedResult4 = "a.doSomething(); /*1*/b.doSomething();";

    assertEquals("Multi line match in replacement", expectedResult4, replace(str7, str8, str9));

    String str10 = """
          parentNode.insert(compositeNode, i);
          if (asyncMode) {
             myTreeModel.nodesWereInserted(parentNode,new int[] {i} );
          }\
      """;
    String str11 = """
          '_parentNode.insert('_newNode, '_i);
          if (asyncMode) {
             myTreeModel.nodesWereInserted('_parentNode,new int[] {'_i} );
          }\
      """;
    String str12 = "addChild($parentNode$,$newNode$, $i$);";
    String expectedResult5 = "    addChild(parentNode,compositeNode, i);";

    assertEquals("Array initializer replacement", expectedResult5, replace(str10, str11, str12));

    String str13 = "  aaa(5,6,3,4,1,2);";
    String str14 = "aaa('_t{2,2},3,4,'_q{2,2});";
    String str15 = "aaa($q$,3,4,$t$);";
    String expectedResult6 = "  aaa(1,2,3,4,5,6);";

    assertEquals("Parameter multiple match", expectedResult6, replace(str13, str14, str15));

    String str16 = "  int c = a();";
    String str17 = "'_t:a ('_q*,'_p*)";
    String str18 = "$t$($q$,1,$p$)";
    String expectedResult7 = "  int c = a(1);";

    assertEquals("Replacement of init in definition + empty substitution", expectedResult7,
                 replace(str16, str17, str18));

    String str19 = "  aaa(bbb);";
    String str20 = "'_t('_);";
    String str21 = "$t$(ccc);";
    String expectedResult8 = "  aaa(ccc);";

    assertEquals("One substitution replacement", expectedResult8, replace(str19, str20, str21));

    String str22 = "  instance.setAAA(anotherInstance.getBBB());";
    String str23 = "  '_i.'_m:set(.+) ('_a.'_m2:get(.+) ());";
    String str24 = "  $a$.set$m2_1$( $i$.get$m_1$() );";
    String expectedResult9 = "  anotherInstance.setBBB( instance.getAAA() );";

    assertEquals("Reg exp substitution replacement", expectedResult9, replace(str22, str23, str24));

    String str25 = """
        LaterInvocator.invokeLater(new Runnable() {
                public void run() {
                  LOG.info("refreshFilesAsync, modalityState=" + ModalityState.current());
                  myHandler.getFiles().refreshFilesAsync(new Runnable() {
                    public void run() {
                      semaphore.up();
                    }
                  });
                }
              });\
      """;
    String str26 = "  LaterInvocator.invokeLater('_Params{1,10});";
    String str27 = "  com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater($Params$);";
    String expectedResult10 = """
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  LOG.info("refreshFilesAsync, modalityState=" + ModalityState.current());
                  myHandler.getFiles().refreshFilesAsync(new Runnable() {
                    public void run() {
                      semaphore.up();
                    }
                  });
                }
              });\
      """;

    assertEquals("Anonymous in parameter", expectedResult10, replace(str25, str26, str27));

    String str28 = """
      UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile,
                                                                processedElement.getTextOffset(), true,
                                                                !myUsageViewDescriptor.toMarkInvalidOrReadonlyUsages(), null);""";
    String str29 = "new UTElementNode('_param, '_directory, '_null, '_0, '_true, !'_descr.toMarkInvalidOrReadonlyUsages(),\n" +
                   "  '_referencesWord)";
    String str30 = "new UTElementNode($param$, $directory$, $null$, $0$, $true$, true,\n" +
                   "  $referencesWord$)";

    String expectedResult11 =
      "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile, processedElement.getTextOffset(), true, true,\n" +
      "  null);";
    assertEquals("Replace in def initializer", expectedResult11, replace(str28, str29, str30));

    String s31 = "a = b; b = c; a=a; c=c;";
    String s32 = "'_a = '_a;";
    String s33 = "1 = 1;";
    String expectedResult12 = "a = b; b = c; 1 = 1; 1 = 1;";

    assertEquals("replace silly assignments", expectedResult12, replace(s31, s32, s33));

    String s34 = "ParamChecker.isTrue(1==1, \"!!!\");";
    String s35 = "ParamChecker.isTrue('_expr, '_msg);";
    String s36 = "assert $expr$ : $msg$;";

    String expectedResult13 = "assert 1==1 : \"!!!\";";
    assertEquals("replace with assert", expectedResult13, replace(s34, s35, s36));

    String s37 = """
      try {\s
        ParamChecker.isTrue(1==1, "!!!");
       \s
        // comment we want to leave
       \s
        ParamChecker.isTrue(2==2, "!!!");
      } catch(Exception ex) {}""";
    String s38 = """
      try {
        '_Statement{0,100};
      } catch(Exception ex) {}""";
    String s39 = "$Statement$;";

    String expectedResult14 = """
      ParamChecker.isTrue(1==1, "!!!");
       \s
        // comment we want to leave
       \s
        ParamChecker.isTrue(2==2, "!!!");""";
    assertEquals("remove try with comments inside", expectedResult14, replace(s37, s38, s39));

    String s40 = "ParamChecker.instanceOf(queryKey, GroupBySqlTypePolicy.GroupKey.class);";
    String s41 = "ParamChecker.instanceOf('_obj, '_class.class);";
    String s42 = "assert $obj$ instanceof $class$ : \"$obj$ is an instance of \" + $obj$.getClass() + \"; expected \" + $class$.class;";
    String expectedResult15 =
      "assert queryKey instanceof GroupBySqlTypePolicy.GroupKey : \"queryKey is an instance of \" + queryKey.getClass() + " +
      "\"; expected \" + GroupBySqlTypePolicy.GroupKey.class;";

    assertEquals("Matching/replacing .class literals", expectedResult15, replace(s40, s41, s42));

    String s43 = """
      class Wpd {
        static final String TAG_BEAN_VALUE = "";
      }
      class X {
        XmlTag beanTag = rootTag.findSubTag(Wpd.TAG_BEAN_VALUE);
      }""";
    String s44 = "'_Instance?.findSubTag( '_Parameter:[exprtype( *String ) ])";
    String s45 = "jetbrains.fabrique.util.XmlApiUtil.findSubTag($Instance$, $Parameter$)";
    String expectedResult16 = """
      class Wpd {
        static final String TAG_BEAN_VALUE = "";
      }
      class X {
        XmlTag beanTag = jetbrains.fabrique.util.XmlApiUtil.findSubTag(rootTag, Wpd.TAG_BEAN_VALUE);
      }""";

    assertEquals("Matching/replacing static fields", expectedResult16, replace(s43, s44, s45, true));

    String s46 = """
      Rectangle2D rec = new Rectangle2D.Double(
                      drec.getX(),
                      drec.getY(),
                      drec.getWidth(),
                      drec.getWidth());""";
    String s47 = "$Instance$.$MethodCall$()";
    String s48 = "OtherClass.round($Instance$.$MethodCall$(),5)";
    String expectedResult17 = """
      Rectangle2D rec = new Rectangle2D.Double(
                      OtherClass.round(drec.getX(),5),
                      OtherClass.round(drec.getY(),5),
                      OtherClass.round(drec.getWidth(),5),
                      OtherClass.round(drec.getWidth(),5));""";
    assertEquals("Replace in constructor", expectedResult17, replace(s46, s47, s48));

    String s49 = """
      class A {}
      class B extends A {}
      class C {
        A a = new B();
      }""";
    String s50 = "A '_b = new '_B:*A ();";
    String s51 = "A $b$ = new $B$(\"$b$\");";
    String expectedResult18 = """
      class A {}
      class B extends A {}
      class C {
        A a = new B("a");
      }""";

    assertEquals("Class navigation", expectedResult18, replace(s49, s50, s51, true));

    String s52 = """
      try {
        aaa();
      } finally {
        System.out.println();}
      try {
        aaa2();
      } catch(Exception ex) {
        aaa3();
      }
      finally {
        System.out.println();
      }
      try {
        aaa4();
      } catch(Exception ex) {
        aaa5();
      }
      """;
    String s53 = """
      try { '_a; } finally {
        '_b;
      }""";
    String s54 = "$a$;";
    String expectedResult19 = """
      aaa();
      try {
        aaa2();
      } catch(Exception ex) {
        aaa3();
      }
      finally {
        System.out.println();
      }
      try {
        aaa4();
      } catch(Exception ex) {
        aaa5();
      }
      """;

    options.getMatchOptions().setLooseMatching(false);
    try {
      assertEquals("Try/finally unwrapped with strict matching", expectedResult19, replace(s52, s53, s54));
    }
    finally {
      options.getMatchOptions().setLooseMatching(true);
    }

    String expectedResult19Loose = """
      aaa();
      aaa2();
      try {
        aaa4();
      } catch(Exception ex) {
        aaa5();
      }
      """;
    assertEquals("Try/finally unwrapped with loose matching", expectedResult19Loose, replace(s52, s53, s54));


    String s55 = """
      for(Iterator<String> iterator = stringlist.iterator(); iterator.hasNext();) {
            String str = iterator.next();
            System.out.println( str );
      }""";
    String s56 = """
      for (Iterator<$Type$> $variable$ = $container$.iterator(); $variable$.hasNext();) {
          $Type$ $var$ = $variable$.next();
          $Statements$;
      }""";
    String s57 = """
      for($Type$ $var$:$container$) {
        $Statements$;
      }""";
    String expectedResult20 = """
      for(String str:stringlist) {
        System.out.println( str );
      }""";

    assertEquals("for with foreach", expectedResult20, replace(s55, s56, s57));

    String s58 = """
      class A {
        static Set<String> b_MAP = new HashSet<String>();
        int c;
      }""";
    String s59 = "'a:[ regex( (.*)_MAP ) ]";
    String s60 = "$a_1$_SET";
    String expectedResult21 = """
      class A {
        static Set<String> b_SET = new HashSet<String>();
        int c;
      }""";

    assertEquals("replace symbol in definition", expectedResult21, replace(s58, s59, s60));

    String s64 = "int x = 42;\n" +
                 "int y = 42; // Stuff";
    String s65 = "'_Type '_Variable = '_Value; // '_Comment";
    String s66 = """
      /**
       * $Comment$
       */
      $Type$ $Variable$ = $Value$;""";
    String expectedResult23 = """
      int x = 42;
      /**
       * Stuff
       */
      int y = 42;""";

    assertEquals("Replacement of the comment with javadoc", expectedResult23, replace(s64, s65, s66));

    String s61 = "try { 1=1; } catch(Exception e) { 1=1; } catch(Throwable t) { 2=2; }";
    String s62 = "try { '_a; } catch(Exception e) { '_b; }";
    String s63 = "try { $a$; } catch(Exception1 e) { $b$; } catch(Exception2 e) { $b$; }";
    String expectedResult22 = "try { 1=1; } catch(Exception1 e) { 1=1; } catch(Exception2 e) { 1=1; } catch(Throwable t) { 2=2; }";

    assertEquals("try replacement by another try will leave the unmatched catch", expectedResult22, replace(s61, s62, s63));
  }

  public void testReplaceExpr() {
    String s1 = "new SimpleDateFormat(\"yyyyMMddHHmmss\")";
    String s2 = "'expr";
    String s3 = "new AtomicReference<DateFormat>($expr$)";
    String expectedResult = "new AtomicReference<DateFormat>(new SimpleDateFormat(\"yyyyMMddHHmmss\"))";

    assertEquals("Replacement of top-level expression only", expectedResult, replace(s1, s2, s3));

    String s4 = "get(\"smth\")";
    String s5 = "'expr";
    String s6 = "new Integer($expr$)";
    String expectedResult1 = "new Integer(get(\"smth\"))";

    assertEquals("Replacement of top-level expression only", expectedResult1, replace(s4, s5, s6));

    String in = """
      class X {
        boolean x(int foo, int bar) {
          return foo - 1 < bar - 1;
        }
      }""";
    String what = "'_intA:[exprtype( *int|Integer )] - '_const < '_intB:[exprtype( *int|Integer )] - '_const";
    String by = "$intA$ < $intB$";
    String expected = """
      class X {
        boolean x(int foo, int bar) {
          return foo < bar;
        }
      }""";

    assertEquals("No errors in pattern", expected, replace(in, what, by));
  }

  public void testReplaceRecordComponents() {
    String in1 = """
      record Bar(String field, int i){}
      """;
    String ex1 = """
      record Bar(String field, int i) {} // comment
      """;
    assertEquals(ex1, replace(in1, "record '_Record('_Type '_component* ) {}", "record $Record$($Type$ $component$) {} // comment"));
  }

  public void testReplaceParameter() {
    String in1 = """
      class A {
        void b(int c,
               int d, /*1*/ int e) {}
      }""";

    String expected1a = """
      class A {
        void b(int c,
               int d2, /*1*/ int e) {}
      }""";
    assertEquals("replace method parameter", expected1a, replace(in1, "int d;", "int d2;"));

    String expected1b = """
      class A {
        void b(int /*!*/ c,
               int /*!*/ d, /*1*/ int /*!*/ e) {}
      }""";
    assertEquals(expected1b, replace(in1, "void b('_T '_v*);", "void b($T$ /*!*/ $v$);"));

    String expected1c = """
      class A {
        void /**/ b(int c,
               int d, /*1*/ int e) {}
      }""";
    assertEquals("replace multi match parameter", expected1c, replace(in1, "void b(int '_x*);", "void /**/ b(int $x$);"));

    String expected1d = """
      class A {
        void b(int c,
               int d, /*1*/ int e) {}
      void c(int c,
               int d, /*1*/ int e) {}
      }""";
    assertEquals("replace multiple occurrences of the same variable", expected1d, replace(in1, "void b('_T '_p*);", "void b($T$ $p$);\n" +
                                                                                                                    "void c($T$ $p$) {}"));

    String in2 = """
      class X {
        void x() {}
      }""";
    String expected2 = """
      class X {
        void /**/ x() {}
      }""";
    assertEquals("replace no match parameter", expected2, replace(in2, "void x(int '_a*);", "void /**/ x() {}"));

    String in3 = """
      class X {
        void x(String s, Integer i) {}
      }""";
    String expected3 = """
      class X {
        void x(List<String> /*>*/ s, List<Integer> /*>*/ i) {}
      }""";
    assertEquals(expected3, replace(in3, "void x('_T '_v*);", "void x(List<$T$> /*>*/ $v$);"));

    String in4 = """
      class X {
        void a(Map<String, Integer> b, Map<String, Integer> c) {}
      }""";
    String expected4 = "class X {\n" +
                       "  void a(Map<String, Integer> /*!*/ b, Map<, > /*!*/ c) {}\n" + // todo fix replacement of second parameter type
                       "}";
    assertEquals(expected4, replace(in4, "void a('_T<'_K, '_V> '_p*);", "void a($T$<$K$, $V$> /*!*/ $p$);"));
  }

  public void testReplaceWithComments() {
    String s1 = "map.put(key, value); // line 1";
    String s2 = "map.put(key, value); // line 1";
    String s3 = "map.put(key, value); // line 1";
    String expectedResult = "map.put(key, value); // line 1";

    assertEquals("replace self with comment after", expectedResult, replace(s1, s2, s3));

    String s4 = "if (true) System.out.println(\"1111\"); else System.out.println(\"2222\");\n" +
                "while(true) System.out.println(\"1111\");";
    String s5 = "System.out.println('_Test);";
    String s6 = "/* System.out.println($Test$); */";
    String expectedResult2 = "if (true) /* System.out.println(\"1111\"); */; else /* System.out.println(\"2222\"); */;\n" +
                             "while(true) /* System.out.println(\"1111\"); */;";
    assertEquals("replace with comment", expectedResult2, replace(s4, s5, s6));
    
    String source1 = """
      public class AnotherTestClass extends TestClass {
          /* Test comment */
          public void testMeThOd() {
              int x = 0;
          }
      }""";
    String what1 = """
      public class '_A extends '_B {
          /* '_Comment */
          public void '_Method() {
              '_statement;
          }
      }""";
    String by1 = """
      public class $A$ extends $B$ {
          /* $Comment$ */
          public void $NewMethod$() {
              $statement$;
          }
      }""";
    final ReplacementVariableDefinition variable = options.addNewVariableDefinition("NewMethod");
    variable.setScriptCodeConstraint("Method.name.toLowerCase()");
    assertEquals("""
                   public class AnotherTestClass extends TestClass {
                       /* Test comment */
                       public void testmethod() {
                           int x = 0;
                       }
                   }""", replace(source1, what1, by1));
  }

  public void testSeveralStatements() {
    String s1 = """
      {
              System.out.println(1);
              System.out.println(2);
              System.out.println(3);
            }
      {
              System.out.println(1);
              System.out.println(2);
              System.out.println(3);
            }
      {
              System.out.println(1);
              System.out.println(2);
              System.out.println(3);
            }""";
    String s2 =
      """
                System.out.println(1);
                System.out.println(2);
                System.out.println(3);
        """;
    String s3 = """
              System.out.println(3);
              System.out.println(2);
              System.out.println(1);
      """;
    String expectedResult1 = """
      {
          System.out.println(3);
          System.out.println(2);
          System.out.println(1);
      }
      {
          System.out.println(3);
          System.out.println(2);
          System.out.println(1);
      }
      {
          System.out.println(3);
          System.out.println(2);
          System.out.println(1);
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("three statements replacement", expectedResult1, replace(s1, s2, s3));
    options.setToReformatAccordingToStyle(false);

    String s4 = """
      ProgressManager.getInstance().startNonCancelableAction();
          try {
            read(id, READ_PARENT);
            return myViewport.parent;
          }
          finally {
            ProgressManager.getInstance().finishNonCancelableAction();
          }""";
    String s5 = """
      ProgressManager.getInstance().startNonCancelableAction();
          try {
            '_statement{2,2};
          }
          finally {
            ProgressManager.getInstance().finishNonCancelableAction();
          }""";
    String s6 = "$statement$;";
    String expectedResult2 = "read(id, READ_PARENT);\n" +
                             "      return myViewport.parent;";
    assertEquals("extra ;", expectedResult2, replace(s4, s5, s6));

    String s7 = """
      public class A {
          void f() {
              new Runnable() {
                  public void run() {
                      l();
                  }

                  private void l() {
                      int i = 9;
                      int j = 9;
                  }
              };
              new Runnable() {
                  public void run() {
                      l();
                  }

                  private void l() {
                      l();
                      l();
                  }
              };
          }

      }""";
    String s8 = """
      new Runnable() {
          public void run() {
              '_l ();
          }
          private void '_l () {
              '_st{2,2};
          }
      };""";
    String s9 = """
      new My() {
          public void f() {
              $st$;
          }
      };""";

    String expectedResult3 = """
      public class A {
          void f() {
              new My() {
                  public void f() {
                      int i = 9;
                      int j = 9;
                  }
              };
              new My() {
                  public void f() {
                      l();
                      l();
                  }
              };
          }

      }""";
    boolean formatAccordingToStyle = options.isToReformatAccordingToStyle();
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; 2", expectedResult3, replace(s7, s8, s9));

    String s10 = """
      public class A {
          void f() {
              new Runnable() {
                  public void run() {
                      l();
                      l();
                  }
                  public void run2() {
                      l();
                      l();
                  }

              };
              new Runnable() {
                  public void run() {
                      l();
                      l();
                  }
                  public void run2() {
                      l();
                      l();
                  }

              };
      new Runnable() {
                  public void run() {
                      l();
                      l();
                  }
                  public void run2() {
                      l2();
                      l2();
                  }

              };
          }

          private void l() {
              int i = 9;
              int j = 9;
          }
      }

      abstract class My {
          abstract void f();
      }""";
    String s11 = """
      new Runnable() {
                  public void run() {
                      '_l{2,2};
                  }
                  public void run2() {
                      '_l;
                  }

              };""";
    String s12 = """
      new My() {
                  public void f() {
                      $l$;
                  }
              };""";
    String expectedResult4 = """
      public class A {
          void f() {
              new My() {
                  public void f() {
                      l();
                      l();
                  }
              };
              new My() {
                  public void f() {
                      l();
                      l();
                  }
              };
              new Runnable() {
                  public void run() {
                      l();
                      l();
                  }

                  public void run2() {
                      l2();
                      l2();
                  }

              };
          }

          private void l() {
              int i = 9;
              int j = 9;
          }
      }

      abstract class My {
          abstract void f();
      }""";

    assertEquals("same multiple occurrences 2 times", expectedResult4, replace(s10, s11, s12));

    options.setToReformatAccordingToStyle(formatAccordingToStyle);

    String s13 = """
          PsiLock.LOCK.acquire();
          try {
            return value;
          }
          finally {
            PsiLock.LOCK.release();
          }\
      """;
    String s13_2 = """
          PsiLock.LOCK.acquire();
          try {
            if (true) { return value; }
          }
          finally {
            PsiLock.LOCK.release();
          }\
      """;
    String s13_3 = """
          PsiLock.LOCK.acquire();
          try {
            if (true) { return value; }

            if (true) { return value; }
          }
          finally {
            PsiLock.LOCK.release();
          }\
      """;
    String s14 = """
          PsiLock.LOCK.acquire();
          try {
            '_T{1,1000};
          }
          finally {
            PsiLock.LOCK.release();
          }\
      """;
    String s15 = """
      synchronized(PsiLock.LOCK) {
        $T$;
      }""";

    String expectedResult5 = """
      synchronized (PsiLock.LOCK) {
          return value;
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; over return", expectedResult5, replace(s13, s14, s15));
    options.setToReformatAccordingToStyle(false);


    String expectedResult6 = """
      synchronized (PsiLock.LOCK) {
          if (true) {
              return value;
          }
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; over if", expectedResult6, replace(s13_2, s14, s15));
    options.setToReformatAccordingToStyle(false);


    String expectedResult7 = """
      synchronized (PsiLock.LOCK) {
          if (true) {
              return value;
          }

          if (true) {
              return value;
          }
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("newlines in matches of several lines", expectedResult7, replace(s13_3, s14, s15));
    options.setToReformatAccordingToStyle(false);

    String s16 = """
      public class SSTest {
        Object lock;
        public Object getProducts (String[] productNames) {
          synchronized (lock) {
            Object o = new Object ();
            assert o != null;
            return o;
          }
        }
      }""";
    String s16_2 = """
      public class SSTest {
        Object lock;
        public void getProducts (String[] productNames) {
          synchronized (lock) {
            boolean[] v = {true};
          }
        }
      }""";

    String s17 = """
      synchronized(lock) {
        '_Statement*;
      }""";

    String s18 = "$Statement$;";
    String expectedResult8 = """
      public class SSTest {
        Object lock;
        public Object getProducts (String[] productNames) {
          Object o = new Object ();
            assert o != null;
            return o;
        }
      }""";
    String expectedResult8_2 = """
      public class SSTest {
        Object lock;
        public void getProducts (String[] productNames) {
          boolean[] v = {true};
        }
      }""";

    assertEquals("extra ;", expectedResult8, replace(s16, s17, s18));

    assertEquals("missed ;", expectedResult8_2, replace(s16_2, s17, s18));
  }

  public void testSpecialClassReplacement() {
    String in = """
      enum Color {
        RED, GREEN, BLUE
      }
      interface X {
        void x();
      }
      @interface Anno {}
      record R(int i, int j) {}
      """;
    String what = "class 'X {}";
    String by = "/** @author me */\n" +
                "class $X$ {}";
    String expected = """
      /** @author me */
      enum Color {
        RED, GREEN, BLUE
      }
      /** @author me */
      interface X {
        void x();
      }
      /** @author me */
      @interface Anno {}
      /** @author me */
      record R(int i, int j) {}
      """;
    assertEquals("Special class replacement", expected, replace(in, what, by, true));

    String in2 = """
      new ArrayList<String>(null) {
        @Override
        public int hashCode() {
          return super.hashCode();
        }
      }""";
    String by2 = """
      class $X$ {
        public String toString() {
          return "hello";
        }
      }
      """;
    String expected2 = """
      new ArrayList<String>(null){
        public String toString() {
          return "hello";
        }
        @Override
        public int hashCode() {
          return super.hashCode();
        }
      }""";
    assertEquals("Special anonymous class replacement", expected2, replace(in2, what, by2, false));
    assertTrue(true);
  }

  public void testClassReplacement() {
    options.setToReformatAccordingToStyle(true);

    String s1 = "class A { public void b() {} }";
    String s2 = "class 'a { '_Other* }";
    String s3 = "class $a$New { Logger LOG; $Other$ }";
    String expectedResult = """
      class ANew {
          Logger LOG;

          public void b() {
          }
      }""";
    assertEquals("Basic class replacement", expectedResult, replace(s1, s2, s3, true));

    String s4 = "class A { class C {} public void b() {} int f; }";
    String s5 = "class 'a { '_Other* }";
    String s6 = "class $a$ { Logger LOG; $Other$ }";
    String expectedResult2 = """
      class A {
          Logger LOG;

          class C {
          }

          public void b() {
          }

          int f;
      }""";

    assertEquals("Order of members in class replacement", expectedResult2, replace(s4, s5, s6, true));

    String s7 = "class A extends B { int c; void b() {} { a = 1; } }";
    String s8 = "class 'A extends B { '_Other* }";
    String s9 = "class $A$ extends B2 { $Other$ }";
    String expectedResult3 = """
      class A extends B2 {
          int c;

          void b() {
          }

          {
              a = 1;
          }
      }""";

    assertEquals("Unsupported pattern exception", expectedResult3, replace(s7, s8, s9, true));

    String s10 = """
      /** @example */
      class A {
        class C {}
        public void b() {}
        int f;
      }""";
    String s11 = "class 'a { '_Other* }";
    String s12 = """
      public class $a$ {
        $Other$
      }""";
    String expectedResult4 = """
      /**
       * @example
       */
      public class A {
          class C {
          }

          public void b() {
          }

          int f;
      }""";

    options.setToReformatAccordingToStyle(true);
    assertEquals("Make class public", expectedResult4, replace(s10, s11, s12, true));
    options.setToReformatAccordingToStyle(false);

    String s13 = """
      class CustomThread extends Thread {
      public CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {
          super(CustomThreadGroup.getThreadGroup(), "CustomThread");
          setDaemon(true);
          if (in instanceof BufferedInputStream) {
              bis = (BufferedInputStream)in;
          } else {
          bis = new BufferedInputStream(in);
          }
          this.out = out;
          this.closeOutOnExit = closeOutOnExit;
      }
      }""";
    String s14 = """
      class '_Class extends Thread {
        '_Class('_ParameterType '_ParameterName*) {
      \t  super (CustomThreadGroup.getThreadGroup(), '_superarg* );
          '_Statement*;
        }
      }""";
    String s15 = """
      class $Class$ extends CustomThread {
        $Class$($ParameterType$ $ParameterName$) {
      \t  super($superarg$);
          $Statement$;
        }
      }""";

    String expectedResult5 = """
      class CustomThread extends CustomThread {
          CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {
              super("CustomThread");
              setDaemon(true);
              if (in instanceof BufferedInputStream) {
                  bis = (BufferedInputStream) in;
              } else {
                  bis = new BufferedInputStream(in);
              }
              this.out = out;
              this.closeOutOnExit = closeOutOnExit;
          }
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Constructor replacement", expectedResult5, replace(s13, s14, s15, true));
    options.setToReformatAccordingToStyle(false);

    String s16 = "public class A {}\n" +
                 "final class B {}";
    String s17 = "class 'A { '_Other* }";
    String s17_2 = "class 'A { private Log log = LogFactory.createLog(); '_Other* }";
    String s18 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";
    String s18_2 = "class $A$ { $Other$ }";

    String expectedResult6 = "public class A { private Log log = LogFactory.createLog();  }\n" +
                             "final class B { private Log log = LogFactory.createLog();  }";
    assertEquals("Modifier list for class", expectedResult6, replace(s16, s17, s18));

    String expectedResult7 = "public class A {  }\n" +
                             "final class B {  }";
    assertEquals("Removing field", expectedResult7, replace(expectedResult6, s17_2, s18_2));

    String s19 = "public class A extends Object implements Cloneable {}\n";
    String s20 = "class 'A { '_Other* }";
    String s21 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";

    String expectedResult8 = "public class A extends Object implements Cloneable { private Log log = LogFactory.createLog();  }\n";
    assertEquals("Extends / implements list for class", expectedResult8, replace(s19, s20, s21, true));

    String s22 = "public class A<T> { int Afield; }\n";
    String s23 = "class 'A { '_Other* }";
    String s24 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";

    String expectedResult9 = "public class A<T> { private Log log = LogFactory.createLog(); int Afield; }\n";
    assertEquals("Type parameters for the class", expectedResult9, replace(s22, s23, s24));

    String s25 = """
      class A {
        // comment before
        protected short a; //  comment after
      }""";
    String s26 = "short a;";
    String s27 = "Object a;";
    String expectedResult10 = """
      class A {
        // comment before
        protected Object a; //  comment after
      }""";

    assertEquals("Replacing dcl with saving access modifiers", expectedResult10, replace(s25, s26, s27));

    String s28 = "aaa";
    String s29 = """
      class 'Class {
       'Class('_ParameterType '_ParameterName) {
          'Class('_ParameterName);
        }
      }""";
    String s30 = """
      class $Class$ {
        $Class$($ParameterType$ $ParameterName$) {
           this($ParameterName$);
        }
      }""";
    String expectedResult11 = "aaa";

    assertEquals("Complex class replacement", expectedResult11, replace(s28, s29, s30));

    String s31 = """
      class A {
        int a; // comment
        char b;
        int c; // comment2
      }""";

    String s32 = "'_Type 'Variable = '_Value?; //'_Comment";
    String s33 = "/**$Comment$*/\n" +
                 "$Type$ $Variable$ = $Value$;";

    String expectedResult12 = """
      class A {
          /**
           * comment
           */
          int a;
          char b;
          /**
           * comment2
           */
          int c;
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Replacing comments with javadoc for fields", expectedResult12, replace(s31, s32, s33, true));
    options.setToReformatAccordingToStyle(false);

    String s34 = """
      /**
       * This interface stores XXX
       * <p/>
       */
      public interface X {
          public static final String HEADER = Headers.HEADER;

      }""";

    String s35 = """
      public interface '_MessageInterface {
          public static final String '_X = '_VALUE;
          '_blah*}""";
    String s36 = """
      public interface $MessageInterface$ {
          public static final String HEADER = $VALUE$;
          $blah$
      }""";

    String expectedResult13 = """
      /**
       * This interface stores XXX
       * <p/>
       */
      public interface X {
          public static final String HEADER = Headers.HEADER;
         \s
      }""";

    assertEquals("Replacing interface with interface, saving comments properly", expectedResult13, replace(s34, s35, s36, true));
  }

  @SuppressWarnings("unused")
  public void _testClassReplacement3() {
    String s37 = "class A { int a = 1; void B() {} int C(char ch) { int z = 1; } int b = 2; }";

    String s38 = "class 'A { '_T '_M*('_PT '_PN*) { '_S*; } '_O* }";
    String s39 = "class $A$ { $T$ $M$($PT$ $PN$) { System.out.println(\"$M$\"); $S$; } $O$ }";

    String expectedResult14 =
      "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";
    String expectedResult14_2 =
      "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";

    assertEquals("Multiple methods replacement", expectedResult14, replace(s37, s38, s39, true)
    );
  }

  public void testClassReplacement4() {
    String s1 = """
      class A {
        int a = 1;
        int b;
        private int c = 2;
      }""";

    String s2 = "@Modifier(\"packageLocal\") '_Type '_Instance = '_Init?;";
    String s3 = "public $Type$ $Instance$ = $Init$;";

    String expectedResult = """
      class A {
        public int a = 1;
        public int b;
        private int c = 2;
      }""";

    assertEquals("Multiple fields replacement", expectedResult, replace(s1, s2, s3, true));
  }

  public void testClassReplacement5() {
    String s1 = """
      public class X {
          /**
           * zzz
           */
          void f() {

          }
      }""";

    String s2 = """
      class 'c {
          /**
           * zzz
           */
          void f(){}
      }""";
    String s3 = """
      class $c$ {
          /**
           * ppp
           */
          void f(){}
      }""";

    String expectedResult = """
      public class X {
          /**
           * ppp
           */
          void f(){}
      }""";

    assertEquals("Not preserving comment if it is present", expectedResult, replace(s1, s2, s3, true));
  }

  public void testClassReplacement6() {
    String s1 = """
      public class X {
         /**
          * zzz
          */
         private void f(int i) {
             //s
         }
      }""";

    String s2 = """
      class 'c {
         /**
          * zzz
          */
         void f('_t '_p){'_s+;}
      }""";
    String s3 = """
      class $c$ {
         /**
          * ppp
          */
         void f($t$ $p$){$s$;}
      }""";

    String expectedResult = """
      public class X {
         /**
          * ppp
          */
         private void f(int i){//s
      }
      }""";

    assertEquals("Correct class replacement", expectedResult, replace(s1, s2, s3));

    String s1_2 = """
      public class X {
         /**
          * zzz
          */
         private void f(int i) {
             int a = 1;
             //s
         }
      }""";
    String expectedResult2 = """
      public class X {
         /**
          * ppp
          */
         private void f(int i){int a = 1;
             //s
      }
      }""";

    assertEquals("Correct class replacement, 2", expectedResult2, replace(s1_2, s2, s3));
  }

  public void testClassReplacement7() {
    String s1 = """
      /**
      * Created by IntelliJ IDEA.
      * User: cdr
      * Date: Nov 15, 2005
      * Time: 4:23:29 PM
      * To change this template use File | Settings | File Templates.
      */
      public class CC {
        /** My Comment */ int a = 3; // aaa
        // bbb
        long c = 2;
        void f() {
        }
      }""";
    String s2 = """
      /**
      * Created by IntelliJ IDEA.
      * User: '_USER
      * Date: '_DATE
      * Time: '_TIME
      * To change this template use File | Settings | File Templates.
      */
      class 'c {
        '_other*
      }""";
    String s3 = """
      /**
      * by: $USER$
      */
      class $c$ {
        $other$
      }""";
    String expectedResult = """
      /**
      * by: cdr
      */
      public class CC {
        /** My Comment */ int a = 3; // aaa
        // bbb
        long c = 2;
        void f() {
        }
      }""";

    assertEquals("Class with comment replacement", expectedResult, replace(s1, s2, s3, true));
  }

  public void testClassReplacement8() {
    String s1 = """
      public class CC {
         /** AAA*/ int b = 1; // comment
      }""";
    String s2 = "int b = 1;";
    String s3 = "long c = 2;";
    String expectedResult = """
      public class CC {
         /** AAA*/ long c = 2; // comment
      }""";

    assertEquals("Class field replacement with simple pattern", expectedResult, replace(s1, s2, s3, true));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/";
  }

  public void testClassReplacement9() throws IOException {
    String s1 = loadFile("before1.java");
    getCurrentCodeStyleSettings().getCustomSettings(JavaCodeStyleSettings.class).JD_KEEP_EMPTY_LINES = false;
    String s2 = """
      class 'A extends '_TestCaseCass:[regex( .*TestCase ) ] {
        '_OtherStatement*;
        public void '_testMethod*:[regex( test.* )] () {
        }
        '_OtherStatement2*;
      }""";
    String s3 = """
      class $A$ extends $TestCaseCass$ {
          $OtherStatement$;
          $OtherStatement2$;
      }""";
    String expectedResult = loadFile("after1.java");

    options.setToReformatAccordingToStyle(true);
    assertEquals("Class replacement 9", expectedResult, replace(s1, s2, s3, true));
  }

  public void testReplaceReturnWithArrayInitializer() {
    String searchIn = "return ( new String[]{CoreVars.CMUAudioPort + \"\"} );";
    String searchFor = "return ( '_A );";
    String replaceBy = "return $A$;";
    String expectedResult = "return new String[]{CoreVars.CMUAudioPort + \"\"};";

    assertEquals("ReplaceReturnWithArrayInitializer", expectedResult, replace(searchIn, searchFor, replaceBy));
  }

  @SuppressWarnings("unused")
  public void _testClassReplacement10() throws IOException {
    String s1 = loadFile("before2.java");
    String s2 = """
      class '_Class {
        '_ReturnType '_MethodName+('_ParameterType '_Parameter*){
          '_content*;
        }
        '_remainingclass*}""";
    String s3 = """
      class $Class$ {
        $remainingclass$
        @Override $ReturnType$ $MethodName$($ParameterType$ $Parameter$){
          $content$;
        }
      }""";
    String expectedResult = loadFile("after2.java");

    options.setToReformatAccordingToStyle(true);
    assertEquals("Class replacement 10", expectedResult, replace(s1, s2, s3, true));
  }

  public void testCatchReplacement() {
    String s1 = """
      try {
        aaa();
      } catch(Exception ex) {
        LOG.assertTrue(false);
      }""";
    String s2 = "{  LOG.assertTrue(false); }";
    String s3 = "{  if (false) LOG.assertTrue(false); }";
    String expectedResult = """
      try {
        aaa();
      } catch (Exception ex) {
          if (false) LOG.assertTrue(false);
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Catch replacement by block", expectedResult, replace(s1, s2, s3));
    options.setToReformatAccordingToStyle(false);
  }

  public void testSavingAccessModifiersDuringClassReplacement() {
    String s43 = """
      public @Deprecated class Foo implements Comparable<Foo> {
        int x;
        void m(){}
      }""";
    String s44 = "class 'Class implements '_Interface { '_Content* }";
    String s45 = """
      @MyAnnotation
      class $Class$ implements $Interface$ {
        $Content$
      }""";
    String expectedResult16 = """
      @MyAnnotation public @Deprecated
      class Foo implements Comparable<Foo> {
        int x;
        void m(){}
      }""";

    assertEquals(
      "Preserving var modifiers and generic information in type during replacement",
      expectedResult16,
      replace(s43, s44, s45, true)
    );

    String in1 = """
      public class A {
        public class B {}
      }""";
    String what1 = """
      class '_A {
        class '_B {}
      }""";
    String by1 = """
      class $A$ {
        private class $B$ {}
      }""";
    String expected1 = """
      public class A {
        private class B {}
      }""";
    assertEquals("No illegal modifier combinations during replacement", expected1,
                 replace(in1, what1, by1));
  }

  public void testDontRequireSpecialVarsForUnmatchedContent() {

    String s43 = """
      public @Deprecated class Foo implements Comparable<Foo> {
        int x;
        void m(){}
       }""";
    String s44 = "class 'Class implements '_Interface {}";
    String s45 = "@MyAnnotation\n" +
                 "class $Class$ implements $Interface$ {}";
    String expectedResult16 = """
      @MyAnnotation public @Deprecated
      class Foo implements Comparable<Foo> {
        int x;
        void m(){}
       }""";

    assertEquals(
      "Preserving class modifiers and generic information in type during replacement",
      expectedResult16,
      replace(s43, s44, s45, true)
    );

    String in = """
      public class A {
        int i,j, k;
        void m1() {}

        public void m2() {}
        void m3() {}
      }""";
    String what = """
      class '_A {
        public void '_m();
      }""";
    String by = """
      class $A$ {
      \tprivate void $m$() {}
      }""";
    assertEquals("Should keep member order when replacing",
                 """
                   public class A {
                     int i ,j , k;
                     void m1() {}

                     private void m2() {}
                     void m3() {}
                   }""",
                 replace(in, what, by));
  }

  public void testClassReplacement2() {
    String s40 = """
      class A {
        /* special comment*/
        private List<String> a = new ArrayList();
        static {
          int a = 1;  }
      }""";
    String s41 = """
      class '_Class {
        '_Stuff2*
        '_FieldType '_FieldName = '_Init?;
        static {
          '_Stmt*;
        }
        '_Stuff*
      }""";
    String s42 = """
      class $Class$ {
        $Stuff2$
        $FieldType$ $FieldName$ = build$FieldName$Map();
        private static $FieldType$ build$FieldName$Map() {
          $FieldType$ $FieldName$ = $Init$;
          $Stmt$;
          return $FieldName$;
        }
        $Stuff$
      }""";
    String expectedResult15 = """
      class A {
       \s
        /* special comment*/
        private List<String> a = buildaMap();
        private static List<String> buildaMap() {
          List<String> a = new ArrayList();
          int a = 1;
          return a;
        }
       \s
      }""";

    assertEquals("Preserving var modifiers and generic information in type during replacement",
                 expectedResult15, replace(s40, s41, s42, true));

    String s46 = "class Foo { int xxx; void foo() { assert false; } void yyy() {}}";
    String s47 = "class '_Class { void '_foo:[regex( foo )](); }";
    String s48 = "class $Class$ { void $foo$(int a); }";
    String expectedResult17 = "class Foo { int xxx; void foo(int a) { assert false; } void yyy() {}}";

    assertEquals(
      "Preserving method bodies",
      expectedResult17,
      replace(s46, s47, s48, true)
    );
  }

  public void testReplaceExceptions() {
    String s1 = "a=a;";
    String s2 = "'a";
    String s3 = "$b$";

    try {
      replace(s1, s2, s3);
      fail("Undefined replace variable is not checked");
    }
    catch (MalformedPatternException ignored) {
    }

    String s4 = "a=a;";
    String s5 = "a=a;";
    String s6 = "a=a";

    try {
      replace(s4, s6, s5);
      fail("Undefined no ; in search");
    }
    catch (UnsupportedPatternException ignored) {
    }

    try {
      replace(s4, "'_Instance.'MethodCall('_Parameter*);", "$Instance$.$MethodCall$($Parameter$);");
      fail("Method call expression target can't be replaced with statement");
    }
    catch (UnsupportedPatternException ignored) {
    }
  }

  public void testActualParameterReplacementInConstructorInvokation() {
    String s1 = """
      filterActions[0] = new Action(TEXT,
          LifeUtil.getIcon("search")) {
              void test() {
                  int a = 1;
              }
      };""";
    String s2 = "LifeUtil.getIcon(\"search\")";
    String s3 = "StdIcons.SEARCH_LIFE";
    String expectedResult = """
      filterActions[0] = new Action(TEXT,
              StdIcons.SEARCH_LIFE) {
              void test() {
                  int a = 1;
              }
      };""";
    options.setToReformatAccordingToStyle(true);
    options.setToShortenFQN(true);

    assertEquals("Replace in anonymous class parameter", expectedResult, replace(s1, s2, s3));
    options.setToShortenFQN(false);
    options.setToReformatAccordingToStyle(false);
  }

  public void testRemove() {
    String s1 = """
      class A {
        /* */
        void a() {
        }
        /*
        */
        int b = 1;
        /*
         *
         */
         class C {}
        {
          /* aaa */
          int a;
          /* */
          a = 1;
        }
      }""";
    String s2 = "/* 'a:[regex( .* )] */";
    String s2_2 = "/* */";
    String s3 = "";
    String expectedResult = """
      class A {
          void a() {
          }

          int b = 1;

          class C {
          }

          {
              int a;
              a = 1;
          }
      }""";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Removing comments", expectedResult, replace(s1, s2, s3));
    options.setToReformatAccordingToStyle(false);


    String expectedResult2 = """
      class A {
        void a() {
        }
        int b = 1;
        /*
         *
         */
         class C {}
        {
          /* aaa */
          int a;
          a = 1;
        }
      }""";

    assertEquals("Removing comments", expectedResult2, replace(s1, s2_2, s3));
  }

  public void testTryCatchInLoop() {
    String code = """
      for (int i = 0; i < MIMEHelper.MIME_MAP.length; i++)
      {
        String s = aFileNameWithOutExtention + MIMEHelper.MIME_MAP[i][0][0];
        try
        {
          if (ENABLE_Z107_READING)
          { in = aFileNameWithOutExtention.getClass().getResourceAsStream(s); }
          else
          { data = ResourceHelper.readResource(s); }
          mime = MIMEHelper.MIME_MAP[i][1][0];
          break;
        }
        catch (final Exception e)
        { continue; }
      }""";
    String toFind = "try { '_TryStatement*; } catch(Exception '_ExceptionDcl) { '_CatchStatement*; }";
    String replacement = "try { $TryStatement$; }\n" + "catch(Throwable $ExceptionDcl$) { $CatchStatement$; }";
    String expectedResult = """
      for (int i = 0; i < MIMEHelper.MIME_MAP.length; i++)
      {
        String s = aFileNameWithOutExtention + MIMEHelper.MIME_MAP[i][0][0];
        try { if (ENABLE_Z107_READING)
          { in = aFileNameWithOutExtention.getClass().getResourceAsStream(s); }
          else
          { data = ResourceHelper.readResource(s); }
          mime = MIMEHelper.MIME_MAP[i][1][0];
          break; }
      catch(final Throwable e) { continue; }
      }""";

    assertEquals("Replacing try/catch in loop", expectedResult, replace(code, toFind, replacement));
  }

  public void testUseStaticImport() {
    final String in = "class X {{ Math.abs(-1); }}";
    final String what = "Math.abs('_a)";
    final String by = "Math.abs($a$)";
    options.setToUseStaticImport(true);
    final String expected = """
      import static java.lang.Math.abs;

      class X {{ abs(-1); }}""";
    assertEquals("Replacing with static import", expected, replace(in, what, by, true, true));

    final String in2 = "class X { void m(java.util.Random r) { Math.abs(r.nextInt()); }}";
    final String expected2 = """
      import static java.lang.Math.abs;

      class X { void m(java.util.Random r) { abs(r.nextInt()); }}""";
    assertEquals("don't add broken static imports", expected2, replace(in2, what, by, true, true));

    final String by2 = "new java.util.Map.Entry() {}";
    final String expected3 = """
      import static java.util.Map.Entry;

      class X {{ new Entry() {}; }}""";
    assertEquals("", expected3, replace(in, what, by2, true, true));

    final String in3 = """
      import java.util.Collections;
      class X {
        void m() {
          System.out.println(Collections.<String>emptyList());
        }
      }""";
    final String what3 = "'_q.'_method:[regex( println )]('_a)";
    final String by3 = "$q$.$method$($a$)";
    final String expected4 = """
      import java.util.Collections;

      import static java.lang.System.out;

      class X {
        void m() {
          out.println(Collections.<String>emptyList());
        }
      }""";
    assertEquals("don't break references with type parameters", expected4,
                 replace(in3, what3, by3, true, true));

    final String in4 = """
      import java.util.Collections;
      public class X {
          void some() {
              System.out.println(1);
              boolean b = Collections.eq(null, null);
          }
      }""";
    final String what4 = "System.out.println(1);";
    final String by4 = "System.out.println(2);";
    final String expected5 = """
      import java.util.Collections;

      import static java.lang.System.out;

      public class X {
          void some() {
              out.println(2);
              boolean b = Collections.eq(null, null);
          }
      }""";
    assertEquals("don't add static import to inaccessible members", expected5,
                 replace(in4, what4, by4, true, true));

    final String in5 = """
      package cz.ahoj.sample.annotations;
      /**
       * @author Ales Holy
       * @since 18. 7. 2017.
       */
      @OuterAnnotation({
              @InnerAnnotation(classes = {Integer.class}),
              @InnerAnnotation(classes = {String.class}),
              @InnerAnnotation(classes = {ReplacementTest.ReplacementTestConfig.class})
      })
      public class ReplacementTest {
          static class ReplacementTestConfig {
          }
      }
      @interface InnerAnnotation {
          Class<?>[] classes() default {};
      }
      @interface OuterAnnotation {

          InnerAnnotation[] value();
      }""";
    final String what5 = "@'_a:[regex( InnerAnnotation )](classes = { String.class })";
    final String by5 = "@$a$(classes = { Integer.class })\n" +
                       "@$a$(classes = { String.class })";
    assertEquals("add import when reference is just outside the class",

                 """
                   package cz.ahoj.sample.annotations;

                   import static cz.ahoj.sample.annotations.ReplacementTest.ReplacementTestConfig;

                   /**
                    * @author Ales Holy
                    * @since 18. 7. 2017.
                    */
                   @OuterAnnotation({
                           @InnerAnnotation(classes = {Integer.class}),
                           @InnerAnnotation(classes = { Integer.class }),
                   @InnerAnnotation(classes = { String.class }),
                           @InnerAnnotation(classes = {ReplacementTestConfig.class})
                   })
                   public class ReplacementTest {
                       static class ReplacementTestConfig {
                       }
                   }
                   @interface InnerAnnotation {
                       Class<?>[] classes() default {};
                   }
                   @interface OuterAnnotation {

                       InnerAnnotation[] value();
                   }""",
                 replace(in5, what5, by5, true, true));

    final String in6 = """
      class X {{
        Predicate<String> p = Integer::valueOf;
      }}
      interface Predicate<T> {
        boolean test(T t);
      }""";
    final String what6 = "Integer::valueOf";
    final String by6 = "Boolean::valueOf";
    assertEquals("""
                   class X {{
                     Predicate<String> p = Boolean::valueOf;
                   }}
                   interface Predicate<T> {
                     boolean test(T t);
                   }""",
                 replace(in6, what6, by6, true));
  }

  public void testUseStaticStarImport() {
    final String in = """
      class ImportTest {{
          Math.abs(-0.5);
          Math.sin(0.5);
          Math.max(1, 2);
      }}""";
    final String what = "Math.'m('_a*)";
    final String by = "Math.$m$($a$)";
    final boolean save = options.isToUseStaticImport();
    options.setToUseStaticImport(true);
    try {

      // depends on default setting being equal to 3 for names count to use import on demand
      final String expected = """
        import static java.lang.Math.*;

        class ImportTest {{
            abs(-0.5);
            sin(0.5);
            max(1, 2);
        }}""";
      assertEquals("Replacing with static star import", expected, replace(in, what, by, true, true));
    }
    finally {
      options.setToUseStaticImport(save);
    }
  }

  public void testReformatAndShortenClassRefPerformance() throws IOException {
    options.setToReformatAccordingToStyle(true);

    final String source = loadFile("ReformatAndShortenClassRefPerformance_source.java");
    final String pattern = loadFile("ReformatAndShortenClassRefPerformance_pattern.java");
    final String replacement = loadFile("ReformatAndShortenClassRefPerformance_replacement.java");

    Benchmark.newBenchmark("SSR Reformat",
                           () -> assertEquals("Reformat Performance", loadFile("ReformatPerformance_result.java"),
                                                             replace(source, pattern, replacement, true, true)))
      .startAsSubtest();

    options.setToReformatAccordingToStyle(false);
    options.setToShortenFQN(true);

    Benchmark.newBenchmark("SSR Shorten Class Reference",
                           () -> assertEquals("Shorten Class Ref Performance", loadFile("ShortenPerformance_result.java"),
                                                             replace(source, pattern, replacement, true, true)))
      .startAsSubtest();
  }

  public void testLeastSurprise() {
    String s1 = """
      @Nullable (a=String.class) @String class Test {
        void aaa(String t) {
          String a = String.valueOf(' ');    String2 a2 = String2.valueOf(' ');  }
      }""";
    String s2 = "'String:String";
    String s2_2 = "String";
    String s2_3 = "'String:java\\.lang\\.String";
    String s2_4 = "java.lang.String";
    String replacement = CommonClassNames.JAVA_UTIL_LIST;
    String expected = """
      @Nullable (a=java.util.List.class) @java.util.List class Test {
        void aaa(java.util.List t) {
          java.util.List a = java.util.List.valueOf(' ');    String2 a2 = String2.valueOf(' ');  }
      }""";

    assertEquals(expected, replace(s1, s2, replacement));
    assertEquals(expected, replace(s1, s2_2, replacement));
    assertEquals(expected, replace(s1, s2_3, replacement));
    assertEquals(expected, replace(s1, s2_4, replacement));
  }

  public void testLeastSurprise2() {
    String s1 = "class B { int s(int a) { a = 1; a = 2; c(a); } }";
    String s2 = "a";
    String replacement = "a2";
    String expected = "class B { int s(int a2) { a2 = 1; a2 = 2; c(a2); } }";

    assertEquals(expected, replace(s1, s2, replacement));
  }

  public void testReplaceTry() {
    String s1 = """
      try {
                  em.persist(p);
              } catch (PersistenceException e) {
                  // good
              }""";
    String s2 = "try { '_TryStatement; } catch('_ExceptionType '_ExceptionDcl) { /* '_CommentContent */ }";
    String replacement =
      "try { $TryStatement$; } catch($ExceptionType$ $ExceptionDcl$) { _logger.warning(\"$CommentContent$\", $ExceptionDcl$); }";
    String expected = "try { em.persist(p); } catch(PersistenceException e) { _logger.warning(\"good\", e); }";

    assertEquals(expected, replace(s1, s2, replacement));

    final String in1 = """
      try {
        System.out.println(1);
      } catch (RuntimeException e) {
        System.out.println(2);
      } finally {
        System.out.println(3);
      }
      """;
    final String what1 = """
      try {
        '_Statement1;
      } finally {
        '_Statement2;
      }""";
    final String by1 = """
      try {
        // comment1
        $Statement1$;
      } finally {
        // comment2
        $Statement2$;
      }""";
    final String expected1 = """
      try {
        // comment1
        System.out.println(1);
      } catch (RuntimeException e) {
        System.out.println(2);
      } finally {
        // comment2
        System.out.println(3);
      }
      """;
    assertEquals("Replacing try/finally should leave unmatched catch sections alone",
                 expected1, replace(in1, what1, by1));

    final String in2 = """
      try (AutoCloseable a = null) {
        System.out.println(1);
      } catch (Exception e) {
        System.out.println(2);
      } finally {
        System.out.println(3);
      }""";
    final String what2 = """
      try {
        '_Statement*;
      }""";
    final String by2 = """
      try {
        /* comment */
        $Statement$;
      }""";
    final String expected2 = """
      try (AutoCloseable a = null) {
        /* comment */
        System.out.println(1);
      } catch (Exception e) {
        System.out.println(2);
      } finally {
        System.out.println(3);
      }""";
    assertEquals("Replacing try/finally should also keep unmatched resource lists and finally blocks",
                 expected2,
                 replace(in2, what2, by2));

    final String in3 = """
      class Foo {
        {
          try {
          } catch (NullPointerException e) {
          } catch (IllegalArgumentException e) {
          } catch (Exception ignored) {
          }
        }
      }""";
    final String what3 = """
      try {
      } catch(Exception ignored) {
      }""";
    final String by3 = """
      try {
        // 1
      } catch(Exception ignored) {
        //2
      }""";
    assertEquals("don't break the order of catch blocks",
                 """
                   class Foo {
                     {
                       try {
                     // 1
                   } catch (NullPointerException e) {
                       } catch (IllegalArgumentException e) {
                       } catch(Exception ignored) {
                     //2
                   }
                     }
                   }""",
                 replace(in3, what3, by3));
  }

  public void testReplaceExtraSemicolon() {
    String in = """
      try {
            String[] a = {"a"};
            System.out.println("blah");
      } finally {
      }
      """;
    String what = """
      try {
       '_statement*;
      } finally {
       \s
      }""";
    String replacement = "$statement$;";
    String expected = """
      String[] a = {"a"};
            System.out.println("blah");
      """;

    assertEquals(expected, replace(in, what, replacement));

    String in2 = """
      try {
          if (args == null) return ;
          while(true) return ;
          System.out.println("blah2");
      } finally {
      }""";
    String expected_2 = """
      if (args == null) return ;
          while(true) return ;
          System.out.println("blah2");""";

    assertEquals(expected_2, replace(in2, what, replacement));

    String in3 = """
      {
          try {
              System.out.println("blah1");

              System.out.println("blah2");
          } finally {
          }
      }""";
    String expected_3 = """
      {
          System.out.println("blah1");

              System.out.println("blah2");
      }""";
    assertEquals(expected_3, replace(in3, what, replacement));

    String in4 = """
      {
          try {
              System.out.println("blah1");
              // indented comment
              System.out.println("blah2");
          } finally {
          }
      }""";
    String expected_4 = """
      {
          System.out.println("blah1");
              // indented comment
              System.out.println("blah2");
      }""";
    assertEquals(expected_4, replace(in4, what, replacement));

    String in5 = """
      class X {
          public void visitDocTag(String tag) {
              String psiDocTagValue = null;
              boolean isTypedValue = false;
              {}
          }
      }""";
    String what5 = """
      void '_m('_T '_p) {
        '_st*;
      }""";
    String replacement5 = """
          void $m$($T$ $p$) {
              System.out.println();
              $st$;
          }\
      """;
    String expected5 = """
      class X {
          public void visitDocTag(String tag) {
              System.out.println();
              String psiDocTagValue = null;
              boolean isTypedValue = false;
              {}
          }
      }""";
    assertEquals(expected5, replace(in5, what5, replacement5));
  }

  public void testReplaceFinalModifier() {
    String s1 = """
      class Foo {
        void foo(final int i,final int i2, final int i3) {
           final int x = 5;
        }
      }""";
    String s2 = "final '_type 'var = '_init?;";
    String s3 = "$type$ $var$ = $init$;";

    String expected = """
      class Foo {
        void foo(int i,int i2, int i3) {
           int x = 5;
        }
      }""";

    assertEquals(expected, replace(s1, s2, s3));
  }

  public void testKeepUnmatchedModifiers() {
    final String in = """
      class X {
        private static final int foo = 1;
      }""";
    final String expected = """
      class X {
        protected static final int foo = 1;
      }""";

    assertEquals(expected, replace(in, "private '_Type '_field = '_init;", "protected $Type$ $field$ = $init$;"));
  }

  public void testRemovingRedundancy() {
    String s1 = """
      int a = 1;
      a = 2;
      int b = a;
      b2 = 3;""";
    String s2 = """
      int '_a = '_i;
      '_st*;
      '_a = '_c;""";
    String s3 = "$st$;\n" +
                "$c$ = $i$;";

    String expected = """
      2 = 1;
      int b = a;
      b2 = 3;""";

    assertEquals(expected, replace(s1, s2, s3));

    String s2_2 = """
      int '_a = '_i;
      '_st*;
      int '_c = '_a;""";
    String s3_2 = "$st$;\n" +
                  "int $c$ = $i$;";
    String expected_2 = """
      a = 2;
      int b = 1;
      b2 = 3;""";

    assertEquals(expected_2, replace(s1, s2_2, s3_2));
  }

  public void testReplaceWithEmptyString() {
    String source = "public class Peepers {\n    public long serialVersionUID = 1L;    \n}";
    String search = "long serialVersionUID = $value$;";
    String replace = "";
    String expectedResult = "public class Peepers {    \n}";

    assertEquals(expectedResult, replace(source, search, replace, true));
  }

  public void testReplaceMultipleFieldsInSingleDeclaration() {
    String source = "abstract class MyClass implements java.util.List {\n  private String a, b;\n}";
    String search = "class 'Name implements java.util.List {\n  '_ClassContent*\n}";
    String replace = "class $Name$ {\n  $ClassContent$\n}";
    String expectedResult = "abstract class MyClass {\n  private String a, b;\n}";

    assertEquals(expectedResult, replace(source, search, replace, true));
  }

  public void testReplaceInImplementsList() {
    String source = """
      import java.io.Externalizable;
      import java.io.Serializable;
      abstract class MyClass implements Serializable, java.util.List, Externalizable {}""";
    String search = "class 'TestCase implements java.util.List, '_others* {\n    '_MyClassContent\n}";
    String replace = "class $TestCase$ implements $others$ {\n    $MyClassContent$\n}";
    String expectedResult = """
      import java.io.Externalizable;
      import java.io.Serializable;
      abstract class MyClass implements Serializable, Externalizable {
         \s
      }""";

    assertEquals(expectedResult, replace(source, search, replace, true));
  }

  public void testReplaceFieldWithEndOfLineComment() {
    String source = """
      class MyClass {
          private String b;// comment
          public void foo() {
          }
      }""";
    String search = "class 'Class {\n    '_Content*\n}";
    String replace = """
      class $Class$ {
          void x() {}
          $Content$
          void bar() {}
      }""";
    String expectedResult = """
      class MyClass {
          void x() {}
          private String b;// comment
          public void foo() {
          }
          void bar() {}
      }""";

    assertEquals(expectedResult, replace(source, search, replace, true));
  }

  public void testReplaceAnnotation() {
    final String in1 = "@SuppressWarnings(\"ALL\")\n" +
                       "public class A {}";
    final String what = "@SuppressWarnings(\"ALL\")";

    final String expected1a = "public class A {}";
    assertEquals(expected1a, replace(in1, what, ""));

    final String expected1b = "@SuppressWarnings(\"NONE\") @Deprecated\n" +
                              "public class A {}";
    assertEquals(expected1b, replace(in1, what, "@SuppressWarnings(\"NONE\") @Deprecated"));

    final String expected1c = """
      @SuppressWarnings("ALL")
      public class B {}""";
    assertEquals("Should replace unmatched annotation parameters",
                 expected1c, replace(in1, "@SuppressWarnings class A {}", "@SuppressWarnings class B {}"));

    final String expected1d = "@ SuppressWarnings(\"ALL\")\n" +
                              "public class A {}";
    assertEquals("Should replace unmatched annotation parameters when matching just annotation",
                 expected1d, replace(in1, "@SuppressWarnings", "@ SuppressWarnings"));

    String what1 = "@SuppressWarnings(\"'value\")";
    String by = "$lower_case$";
    final ReplacementVariableDefinition variable = options.addNewVariableDefinition("lower_case");
    variable.setScriptCodeConstraint("value.getText().toLowerCase()");
    final String expected1e = "@SuppressWarnings(\"all\")\n" +
                              "public class A {}";
    assertEquals(expected1e, replace(in1, what1, by));


    final String in2 = """
      class X {
        @SuppressWarnings("unused") String s;
      }""";
    final String expected2a = """
      class X {
        @SuppressWarnings({"unused", "other"}) String s;
      }""";
    assertEquals(expected2a, replace(in2, "@SuppressWarnings(\"unused\") String '_s;",
                                     "@SuppressWarnings({\"unused\", \"other\"}) String $s$;"));

    final String expected2b = """
      class X {
        @SuppressWarnings("unused") String s = "undoubtedly";
      }""";
    assertEquals(expected2b, replace(in2, "@'_Anno('_v) String '_s;", "@$Anno$($v$) String $s$ = \"undoubtedly\";"));

    final String expected2c = """
      class X {
        @SuppressWarnings(value="unused") String s;
      }""";
    assertEquals(expected2c, replace(in2, "@'_A('_v='_x)", "@$A$($v$=$x$)"));

    final String expected2d = """
      class X {
        @SuppressWarnings({"unused", "raw"}) String s;
      }""";
    assertEquals(expected2d, replace(in2, "@'_A('_x)", "@$A$({$x$, \"raw\"})"));

    final String expected2e = """
      class X {
        @SuppressWarnings(value={1,2}, value="unused") String s;
      }""";
    assertEquals(expected2e, replace(in2, "@'_A('_n='_v)", "@$A$($n$={1,2}, $n$=$v$)"));


    final String in3 = """
      class X {
        @Language(value="RegExp",
                  prefix="xxx") String pattern;
      }""";
    final String expected3 = """
      class X {
        @ A(value="RegExp",
                  prefix="xxx", suffix="") String pattern;
      }""";
    assertEquals(expected3, replace(in3, "@'_A('_v*='_x)", "@ A($v$=$x$, suffix=\"\")"));

    final String in4 = """
      class X {
        @Anno(one=1, two=1) String s;
      }""";
    final String expected4 = """
      class X {
        @Anno(one=1, two=1, three=1) String s;
      }""";
    assertEquals(expected4, replace(in4, "@'_A('_p*=1)", "@$A$($p$=1, three=1)"));

    final String expected4b = """
      class X {
        @Anno(one=2, two=1) String s;
      }""";
    assertEquals(expected4b, replace(in4, "@'_A('_p:one =1)", "@$A$($p$=2)"));

    final String in5 = """
      @RunWith(SpringJUnit4ClassRunner.class)
      @ContextConfiguration(classes = {
              ThisShallBeTwoClassesInContextHierarchyConfig.class,
              SomeTest.SomeTestConfig.class,
              WhateverConfig.class
      })
      @Transactional
      public class SomeTest {}""";
    final String expected5 = """
      @RunWith(SpringJUnit4ClassRunner.class)
      @ContextHierarchy(classes = {
              @ContextConfiguration(classes = {ThisShallBeTwoClassesInContextHierarchyConfig.class,
              SomeTest.SomeTestConfig.class,
              WhateverConfig.class, Object.class})
      })
      @Transactional
      public class SomeTest {}""";
    assertEquals(expected5, replace(in5, "@ContextConfiguration(classes = {'_X*})", """
      @ContextHierarchy(classes = {
              @ContextConfiguration(classes = {$X$, Object.class})
      })"""));

    final String in6 = """
      class X {
        @WastingTime @Override
        public @Constant @Sorrow String value() {
          return null;
        }
      }""";
    final String expected6 = """
      class X {
        @WastingTime @Override
        private @Constant @Sorrow String value() {
          return null;
        }
      }""";
    assertEquals(expected6, replace(in6, "'_ReturnType '_method('_ParameterType '_parameter*);",
                                    "private $ReturnType$ $method$($ParameterType$ $parameter$);"));

    final String in7 = """
      public class IssueLink {
          @XmlAttribute(name = "default", namespace = "space")
          @Deprecated
          public String typeInward;
      }""";
    final String expected7 = """
      public class IssueLink {
          @XmlAttribute(name="default", namespace = "space")
          public String typeInward;
      }""";
    assertEquals(expected7, replace(in7, "@XmlAttribute(name=\"default\") @Deprecated '_Type '_field;",
                                    "@XmlAttribute(name=\"default\") $Type$ $field$;"));

    final String expected7b = """
      class IssueLink {
          @XmlAttribute(name = "default", namespace = "space")
          @Deprecated
          public String typeInward;
      }""";
    assertEquals(expected7b, replace(in7, "@'_Anno* public class '_X {}", "@$Anno$ class $X$ {}"));
  }

  public void testReplacePolyadicExpression() {
    final String in1 = """
      class A {
        int i = 1 + 2 + 3;
      }""";
    final String what1 = "1 + '_a+";

    final String by1 = "4";
    assertEquals("""
                   class A {
                     int i = 4;
                   }""", replace(in1, what1, by1));

    final String by2 = "$a$";
    assertEquals("""
                   class A {
                     int i = 2 + 3;
                   }""", replace(in1, what1, by2));

    final String by3 = "$a$+4";
    assertEquals("""
                   class A {
                     int i = 2 + 3+4;
                   }""", replace(in1, what1, by3));

    final String what2 = "1 + 2 + 3 + '_a*";
    final String by4 = "1 + 3 + $a$";
    assertEquals("""
                   class A {
                     int i = 1 + 3;
                   }""", replace(in1, what2, by4));

    final String by5 = "$a$ + 1 + 3";
    assertEquals("""
                   class A {
                     int i = 1 + 3;
                   }""", replace(in1, what2, by5));

    final String by6 = "1 + $a$ + 3";
    assertEquals("""
                   class A {
                     int i = 1 + 3;
                   }""", replace(in1, what2, by6));

    final String in2 = """
      class A {
        boolean b = true && true;
      }""";
    final String what3 = "true && true && '_a*";
    final String by7 = "true && true && $a$";
    assertEquals("""
                   class A {
                     boolean b = true && true;
                   }""", replace(in2, what3, by7));

    final String by8 = "$a$ && true && true";
    assertEquals("""
                   class A {
                     boolean b = true && true;
                   }""", replace(in2, what3, by8));
  }

  public void testReplaceAssert() {
    final String in = """
      class A {
        void m(int i) {
          assert 10 > i;
        }
      }""";

    final String what = "assert '_a > '_b : '_c?;";
    final String by = "assert $b$ < $a$ : $c$;";
    assertEquals("""
                   class A {
                     void m(int i) {
                       assert i < 10;
                     }
                   }""", replace(in, what, by));
  }

  public void testReplaceMultipleVariablesInOneDeclaration() {
    final String in = """
      class A {
        private int i, /*1*/j, k;
        void m() {
          int i,
              j,// 2
              k;
        }
      }
      """;
    final String what1 = "int '_i+;";
    final String by1 = "float $i$;";
    assertEquals("""
                   class A {
                     private float i, /*1*/j, k;
                     void m() {
                       float i,
                           j,// 2
                           k;
                     }
                   }
                   """,
                 replace(in, what1, by1));

    final String what2 = "int '_a, '_b, '_c = '_d?;";
    final String by2 = "float $a$, $b$, $c$ = $d$;";
    assertEquals("""
                   class A {
                     private float i, j, k;
                     void m() {
                       float i, j, k;
                     }
                   }
                   """,
                 replace(in, what2, by2));
  }

  public void testReplaceWithScriptedVariable() {
    final String in = """
      class A {
        void method(Object... os) {}
        void f(Object a, Object b, Object c) {
          method(a, b, c, "one" + "two");
          method(a);
        }
      }""";
    final String what = "method('_arg+)";
    final String by = "method($newarg$)";
    final ReplacementVariableDefinition variable = options.addNewVariableDefinition("newarg");
    variable.setScriptCodeConstraint("arg.collect { \"(String)\" + it.getText() }.join(',')");

    final String expected = """
      class A {
        void method(Object... os) {}
        void f(Object a, Object b, Object c) {
          method((String)a,(String)b,(String)c,(String)"one" + "two");
          method((String)a);
        }
      }""";
    assertEquals(expected, replace(in, what, by));
    options.clearVariableDefinitions();

    final String in2 = """
      class Limitless {
          public int id;
          public String field;
          public Limitless() {
              this.field = "default";
              this.id = 01;
          }
          public int getId() {
              return id;
          }
          public String getField() { return field; }
          public static void main(String [] args) {
              Limitless myClass = new Limitless();
              System.out.println(myClass.getField()+" "+myClass.getId());
              Example example = new Example(1, "name");
              int r = example.getI()+9;
              myClass.getId();
          }
      }""";
    final String what2 = "'_Instance:[exprtype( Limitless )].'property:[regex( get(.*) )]()";
    final String by2 = "$Instance$.$field$";
    final ReplacementVariableDefinition variable2 = options.addNewVariableDefinition("field");
    variable2.setScriptCodeConstraint("String name = property.methodExpression.referenceName[3..-1]\n" +
                                      "name[0].toLowerCase() + name[1..-1]");
    assertEquals("""
                   class Limitless {
                       public int id;
                       public String field;
                       public Limitless() {
                           this.field = "default";
                           this.id = 01;
                       }
                       public int getId() {
                           return id;
                       }
                       public String getField() { return field; }
                       public static void main(String [] args) {
                           Limitless myClass = new Limitless();
                           System.out.println(myClass.field+" "+myClass.id);
                           Example example = new Example(1, "name");
                           int r = example.getI()+9;
                           myClass.id;
                       }
                   }""", replace(in2, what2, by2));
    options.clearVariableDefinitions();
  }

  public void testMethodContentReplacement() {
    final String in = """
      class A extends TestCase {
        void testOne() {
          System.out.println();
        }
      }
      """;
    final String what = "class '_A { void '_b:[regex( test.* )](); }";
    final String by = "class $A$ {\n  @java.lang.Override void $b$();\n}";
    assertEquals("""
                   class A extends TestCase {
                     @Override void testOne() {
                       System.out.println();
                     }
                   }
                   """, replace(in, what, by, true));

    final String what2 = "void '_a:[regex( test.* )]();";
    final String by2 = "@org.junit.Test void $a$();";
    assertEquals("""
                   class A extends TestCase {
                     @org.junit.Test void testOne() {
                       System.out.println();
                     }
                   }
                   """,
                 replace(in, what2, by2));
  }

  public void testReplaceMethodWithoutBody() {
    final String in = """
      abstract class A {
        abstract void a();
      }""";
    final String what = "void '_a();";
    final String by = "void $a$(int i);";
    assertEquals("""
                   abstract class A {
                     abstract void a(int i);
                   }""",
                 replace(in, what, by));

    final String what2 = "abstract void '_a('_T '_p*);";
    final String by2 = "void $a$($T$ $p$) {}";
    assertEquals("""
                   abstract class A {
                     void a() {}
                   }""",
                 replace(in, what2, by2));
  }

  public void testReplaceParameterWithComment() {
    final String in = """
      class A {
        void a(int b) {}
      }""";
    final String what = "int '_a = '_b{0,1};";
    final String by = "final long /*!*/ $a$ = $b$;";
    assertEquals("""
                   class A {
                     void a(final long /*!*/ b) {}
                   }""",
                 replace(in, what, by));

    final String in2 = """
      class X {
        void m() {
          for (int x : new int[]{1, 2, 3}) {}
        }
      }""";
    final String what2 = "'_T '_v = '_i{0,1};";
    final String by2 = "final $T$ /*!*/ $v$ = $i$;";
    assertEquals("foreach parameter replaced incorrectly",
                 """
                   class X {
                     void m() {
                       for (final int /*!*/ x : new int[]{1, 2, 3}) {}
                     }
                   }""",
                 replace(in2, what2, by2));
  }

  public void testReplaceInnerClass() {
    String in = """
      public class A {
        public class B<T> extends A implements java.io.Serializable {}
      }""";
    String what = """
      class '_A {
        class '_B {}
      }""";
    String by = """
      class $A$ {
        private class $B$ {
        }
      }""";
    assertEquals("""
                   public class A {
                     private class B<T> extends A implements java.io.Serializable {
                     }
                   }""",
                 replace(in, what, by));

    String in2 = """
      public class A {
        void m1() {}
        public void m2() {}
        public class B<T> extends A implements java.io.Serializable {
          int zero() {
            return 0;
          }
        }
        void m3() {}
      }""";
    assertEquals("should replace unmatched class content correctly",
                 """
                   public class A {
                     void m1() {}
                     public void m2() {}
                     private class B<T> extends A implements java.io.Serializable {
                       int zero() {
                         return 0;
                       }
                     }
                     void m3() {}
                   }""",
                 replace(in2, what, by));
  }

  public void testReplaceQualifiedReference() {
    String in = """
      class A {
        String s;
        void setS(String s) {
          System.out.println(this.s);
          this.s = s;
        }
      }""";
    String what = "System.out.println('_a);";
    String by = "System.out.println(\"$a$\" + $a$);";
    assertEquals("don't drop this",
                 """
                   class A {
                     String s;
                     void setS(String s) {
                       System.out.println("this.s" + this.s);
                       this.s = s;
                     }
                   }""",
                 replace(in, what, by));
  }

  public void testReplaceExpressionStatement() {
    String in = """
      class A {
        void m() {
          new Object();
        }
      }""";
    String what = "'_expr;";
    String by = "$expr$.toString();";
    assertEquals("too many semicolons",
                 """
                   class A {
                     void m() {
                       new Object().toString();
                     }
                   }""",
                 replace(in, what, by, true));
  }

  public void testReplaceVariableInitializer() {
    String in = """
      class X {
        private final int i = 1;
      }""";
    String what = "int '_v;";
    String by = "long $v$;";
    assertEquals("initializer should remain",
                 """
                   class X {
                     private final long i=1;
                   }""",
                 replace(in, what, by, true));
  }

  public void testReplaceParentheses() {
    String in = """
      public class MyFile {
          void test(String a, Object b) {
              if(a.length() == 0) {
                  System.out.println("empty");
              }
              if(((String) b).length() == 0) {
                  System.out.println("empty");
              }
          }
      }""";

    String what = "'_expr:[exprtype( String )].length() == 0";
    String by = "$expr$.isEmpty()";
    assertEquals("parentheses should remain",

                 """
                   public class MyFile {
                       void test(String a, Object b) {
                           if(a.isEmpty()) {
                               System.out.println("empty");
                           }
                           if(((String) b).isEmpty()) {
                               System.out.println("empty");
                           }
                       }
                   }""",
                 replace(in, what, by, true));

    options.getMatchOptions().setRecursiveSearch(true);
    String in2 = """
      class X {{
        int i = (((3)));
      }}""";
    String what2 = "('_expr:[exprtype( int )])";
    String by2 = "2";
    assertEquals("don't throw exceptions when replacing",
                 """
                   class X {{
                     int i = 2;
                   }}""",
                 replace(in2, what2, by2, true));
  }

  public void testReplaceTarget() {
    String in = """
      import org.junit.Test;
      class Help {
        private String s = "hello";
        @Test
        public void testThisThing(){
          System.out.println();
          System.out.println();
          System.out.println();
          s = null;
        }
      }""";
    String what = """
      class 'Class {
        '_FieldType '_FieldName;
        @'_Annotation
        '_MethodType '_MethodName() {
          '_Statement*;
          '_FieldName = null;
        }
      }""";
    String by = """
      class $Class$ {
        $FieldType$ $FieldName$;
        @$Annotation$
        $MethodType$ $MethodName$() {
          $Statement$;
        }
      }""";
    assertEquals("""
                   import org.junit.Test;
                   class Help {
                     private String s="hello";
                     @Test
                     public
                     void testThisThing() {
                       System.out.println();
                       System.out.println();
                       System.out.println();
                     }
                   }""", replace(in, what, by, true));
  }

  public void testReplaceGenerics() {
    options.setToShortenFQN(false);
    String in = """
      import java.util.ArrayList;
      import java.util.List;
      class X {
        List<String> list = new java.util.LinkedList<String>();
        List<Integer> list2 = new java.util.ArrayList<Integer>();
        List<Double> list3 = new ArrayList<>();
      }""";

    assertEquals("should properly replace with diamond",
                 """
                   import java.util.ArrayList;
                   import java.util.List;
                   class X {
                     List<String> list = new java.util.LinkedList<>();
                     List<Integer> list2 = new ArrayList<>();
                     List<Double> list3 = new ArrayList<>();
                   }""",
                 replace(in, "new '_X<'_p+>()", "new $X$<>()", true));
    assertEquals("should keep generics when matching without",
                 """
                   import java.util.ArrayList;
                   import java.util.List;
                   class X {
                     List<String> list = new /*1*/java.util.LinkedList<String>();
                     List<Integer> list2 = new /*1*/ArrayList<Integer>();
                     List<Double> list3 = new /*1*/ArrayList<>();
                   }""",
                 replace(in, "new '_X()", "new /*1*/$X$()", true));
    assertEquals("should not duplicate generic parameters",
                 """
                   import java.util.ArrayList;
                   import java.util.List;
                   class X {
                     List<String> list = new java.util.LinkedList</*0*/String>();
                     List<Integer> list2 = new ArrayList</*0*/Integer>();
                     List<Double> list3 = new ArrayList<>();
                   }""",
                 replace(in, "new '_X<'_p+>()", "new $X$</*0*/$p$>()", true));

    String in2 = """
      import java.util.Map;
      import java.util.List;
      import java.util.concurrent.ConcurrentHashMap;
      class X<A, B>{
        void x() {
          Map<String, List<String>> myVar = new ConcurrentHashMap<>(10, 2);
        }
      }
      """;
    assertEquals("replace multiple generic parameters correctly",
                 """
                 import java.util.Map;
                 import java.util.List;
                 import java.util.concurrent.ConcurrentHashMap;
                 class X<A, B>{
                   void x() {
                     var myVar = new ConcurrentHashMap<String, List<String>>(10, 2);
                   }
                 }
                 """,
                 replace(in2,
                         "'_Type<'_GenericArgument+> '_Var = new '_Ctor<>('_Params*);",
                         "var $Var$ = new $Ctor$<$GenericArgument$>($Params$);",
                         true));
    assertEquals("replace multiple class type parameters correctly",
                 """
                   import java.util.Map;
                   import java.util.List;
                   import java.util.concurrent.ConcurrentHashMap;
                   class X<A, B> {
                     void x() {
                       Map<String, List<String>> myVar = new ConcurrentHashMap<>(10, 2);
                     }
                   }
                   """,
                 replace(in2, "class '_C<'_P+> {}", "class $C$<$P$> {}", true));
  }

  public void testArrays() {
    String in = """
      public abstract class Bar {
          String[] x;
          abstract String[] foo(String[] x);
      }""";

    assertEquals("should keep array brackets 1",
                 """
                   public abstract class Bar {
                       String[] x;
                       abstract String[] foo(String[] x);
                   }""",
                 replace(in, "'_FieldType 'Field = '_Init?;", "$FieldType$ $Field$ = $Init$;", true));

    assertEquals("should keep array brackets 2",
                 """
                   public abstract class Bar {
                       String[] x;
                       abstract String[] foo (String[] x);
                   }""",
                 replace(in, "'_ReturnType '_Method('_ParameterType '_Parameter*);",
                         "$ReturnType$ $Method$ ($ParameterType$ $Parameter$);", true));

    String in2 = """
      class X {
        public final X[] EMPTY_ARRAY = {};
      }""";
    assertEquals("shouldn't delete semicolon",
                 """
                   class X {
                     public final X[] EMPTY_ARRAY = {};
                   }""",
                 replace(in2, "'_FieldType 'Field = '_Init?;", "$FieldType$ $Field$ = $Init$;", true));

    String in3 = """
      class X {
        void x(int... ss) {}

        void y() {
          x(new int[] {1, 2});\s
        }
      }
      """;
    assertEquals("Should keep commas",
                 """
                   class X {
                     void x(int... ss) {}
                                      
                     void y() {
                       x(1, 2);\s
                     }
                   }
                   """,
                 replace(in3, "new int[] {'_a*}", "$a$", true));
    assertEquals("Should keep commas 2",
                 """
                   class X {
                     void x(int... ss) {}
                                      
                     void y() {
                       x(new long[] {1, 2});\s
                     }
                   }
                   """,
                 replace(in3, "new int[] {'_a*}", "new long[] {$a$}"));

    String in4 = """
      class X {
        void x(int... ss) {}

        void y() {
          x(1, 2);\s
        }
      }
      """;
    assertEquals("Should keep commas 3",
                 """
                   class X {
                     void x(int... ss) {}
                   
                     void y() {
                       x(new int[] {1, 2});\s
                     }
                   }
                   """,
                 replace(in4, "x('_arg*)", "x(new int[] {$arg$})"));
  }

  public void testMethodCall() {
    String in = """
      class X {
        void x() {}
        void y() {
          x();
          this.x();
        }
      }""";
    assertEquals("replace (un)qualified calls correctly",
                 """
                   class X {
                     void x() {}
                     void y() {
                       x();
                       this.x();
                     }
                   }""",
                 replace(in, "'_Instance?.'_MethodCall('_arguments*)", "$Instance$.$MethodCall$($arguments$)", true));
    String in2 = """
      class X {
        void x() {
          System.out.println("" + Some.x());
        }
        
        static class Some {
          int x() {
            return 1;
          }
        }
      }
      """;
    assertEquals("copy unmatched qualifiers",
                 """
                   class X {
                     void x() {
                       System.out.println(Some.x());
                     }
                                      
                     static class Some {
                       int x() {
                         return 1;
                       }
                     }
                   }
                   """,
                 replace(in2, "System.out.println(\"\"+'_x());", "System.out.println($x$());", true));
  }

  public void testKeepModifierFormatting() {
    String in = "@Deprecated\n" +
                "public class X {}";
    final String what = "class '_X {}";
    final String replacement = "/** comment */\n" +
                               "class $X$ {}";
    final String expected = """
      /** comment */
      @Deprecated
      public class X {}""";
    assertEquals("keep newline in modifier list",
                 expected, replace(in, what, replacement, true));
  }

  public void testTypeParameterReplacement() {
    final String in = """
      class Util {
        @SafeVarargs
        @Contract(pure=true)
        public static <T> T @NotNull [] ar(T @NotNull ... elements) {
          return elements;
        }
      }""";
    final String what = "$RT$ ar($T$ $p$);";
    final String replacement = "$RT$ ar($T$ $p$);";
    assertEquals("keep method type parameters",
                 in, replace(in, what, replacement, true));
  }
}