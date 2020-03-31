// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.CommonClassNames;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Maxim.Mossienko
 */
public class StructuralReplaceTest extends StructuralReplaceTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final MatchOptions matchOptions = options.getMatchOptions();
    matchOptions.setFileType(StdFileTypes.JAVA);
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

    String s7 = "IconLoader.getIcon(\"/ant/property.png\");\n" +
                "IconLoader.getIcon(\"/ant/another/property.png\");\n";
    String s8 = "IconLoader.getIcon(\"/'_module/'_name:[regex( \\w+ )].png\");";
    String s9 = "Icons.$module$.$name$;";
    String expectedResult3 = "Icons.ant.property;\n" +
                             "IconLoader.getIcon(\"/ant/another/property.png\");\n";

    assertEquals("string literal replacement 3", expectedResult3, replace(s7, s8, s9));

    String s10 = "configureByFile(path + \"1.html\");\n" +
                 "    checkResultByFile(path + \"1_after.html\");\n" +
                 "    checkResultByFile(path + \"1_after2.html\");\n" +
                 "    checkResultByFile(path + \"1_after3.html\");";
    String s11 = "\"'a.html\"";
    String s12 = "\"$a$.\"+ext";
    String expectedResult4 = "configureByFile(path + \"1.\"+ext);\n" +
                             "    checkResultByFile(path + \"1_after.\"+ext);\n" +
                             "    checkResultByFile(path + \"1_after2.\"+ext);\n" +
                             "    checkResultByFile(path + \"1_after3.\"+ext);";

    assertEquals("string literal replacement 4", expectedResult4, replace(s10, s11, s12));
  }

  public void testReplace2() {
    String s1 = "package com.www.xxx.yyy;\n" +
                "\n" +
                "import javax.swing.*;\n" +
                "\n" +
                "public class Test {\n" +
                "  public static void main(String[] args) {\n" +
                "    if (1==1)\n" +
                "      JOptionPane.showMessageDialog(null, \"MESSAGE\");\n" +
                "  }\n" +
                "}";
    String s2 = "JOptionPane.'_showDialog(null, '_msg);";
    String s3 = "//FIXME provide a parent frame\n" +
                "JOptionPane.$showDialog$(null, $msg$);";

    String expectedResult = "package com.www.xxx.yyy;\n" +
                            "\n" +
                            "import javax.swing.*;\n" +
                            "\n" +
                            "public class Test {\n" +
                            "  public static void main(String[] args) {\n" +
                            "    if (1==1)\n" +
                            "      //FIXME provide a parent frame\n" +
                            "JOptionPane.showMessageDialog(null, \"MESSAGE\");\n" +
                            "  }\n" +
                            "}";

    assertEquals("adding comment to statement inside the if body", expectedResult, replace(s1, s2, s3));

    String s4 = "myButton.setText(\"Ok\");";
    String s5 = "'_Instance.'_MethodCall:[regex( setText )]('_Parameter*:[regex( \"Ok\" )]);";
    String s6 = "$Instance$.$MethodCall$(\"OK\");";

    String expectedResult2 = "myButton.setText(\"OK\");";

    assertEquals("adding comment to statement inside the if body", expectedResult2, replace(s4, s5, s6));
  }

  public void testReplace() {
    String str = "// searching for several constructions\n" +
                 "    lastTest = \"several constructions match\";\n" +
                 "    matches = testMatcher.findMatches(s5,s4, options);\n" +
                 "    if (matches==null || matches.size()!=3) return false;\n" +
                 "\n" +
                 "    // searching for several constructions\n" +
                 "    lastTest = \"several constructions 2\";\n" +
                 "    matches = testMatcher.findMatches(s5,s6, options);\n" +
                 "    if (matches.size()!=0) return false;\n" +
                 "\n" +
                 "    //options.setLooseMatching(true);\n" +
                 "    // searching for several constructions\n" +
                 "    lastTest = \"several constructions 3\";\n" +
                 "    matches = testMatcher.findMatches(s7,s8, options);\n" +
                 "    if (matches.size()!=2) return false;";

    String str2="      lastTest = '_Descr;\n" +
                "      matches = testMatcher.findMatches('_In,'_Pattern, options);\n" +
                "      if (matches.size()!='_Number) return false;";
    String str3 = "assertEquals($Descr$,testMatcher.findMatches($In$,$Pattern$, options).size(),$Number$);";
    String expectedResult1 = "// searching for several constructions\n" +
                             "lastTest = \"several constructions match\";\n" +
                             "matches = testMatcher.findMatches(s5, s4, options);\n" +
                             "if (matches == null || matches.size() != 3) return false;\n" +
                             "\n" +
                             "// searching for several constructions\n" +
                             "assertEquals(\"several constructions 2\", testMatcher.findMatches(s5, s6, options).size(), 0);\n" +
                             "\n" +
                             "//options.setLooseMatching(true);\n" +
                             "// searching for several constructions\n" +
                             "assertEquals(\"several constructions 3\", testMatcher.findMatches(s7, s8, options).size(), 2);";

    String str4 = "";

    options.setToReformatAccordingToStyle(true);
    assertEquals("Basic replacement with formatter", expectedResult1, replace(str, str2, str3));
    options.setToReformatAccordingToStyle(false);

    String expectedResult2 = "// searching for several constructions\n" +
                             "    lastTest = \"several constructions match\";\n" +
                             "    matches = testMatcher.findMatches(s5,s4, options);\n" +
                             "    if (matches==null || matches.size()!=3) return false;\n" +
                             "\n" +
                             "    // searching for several constructions\n" +
                             "\n" +
                             "    //options.setLooseMatching(true);\n" +
                             "    // searching for several constructions";
    assertEquals("Empty replacement", expectedResult2, replace(str, str2, str4));

    String str5 = "testMatcher.findMatches('_In,'_Pattern, options).size()";
    String str6 = "findMatchesCount($In$,$Pattern$)";
    String expectedResult3="// searching for several constructions\n" +
                           "lastTest = \"several constructions match\";\n" +
                           "matches = testMatcher.findMatches(s5, s4, options);\n" +
                           "if (matches == null || matches.size() != 3) return false;\n" +
                           "\n" +
                           "// searching for several constructions\n" +
                           "assertEquals(\"several constructions 2\", findMatchesCount(s5,s6), 0);\n" +
                           "\n" +
                           "//options.setLooseMatching(true);\n" +
                           "// searching for several constructions\n" +
                           "assertEquals(\"several constructions 3\", findMatchesCount(s7,s8), 2);";
    assertEquals("Expression replacement", expectedResult3, replace(expectedResult1, str5, str6));

    String str7 = "try { a.doSomething(); /*1*/b.doSomething(); } catch(IOException ex) {  ex.printStackTrace(); throw new RuntimeException(ex); }";
    String str8 = "try { '_Statements+; } catch('_ '_) { '_HandlerStatements+; }";
    String str9 = "$Statements$;";
    String expectedResult4 = "a.doSomething(); /*1*/b.doSomething();";

    assertEquals("Multi line match in replacement", expectedResult4, replace(str7, str8, str9));

    String str10 = "    parentNode.insert(compositeNode, i);\n" +
                   "    if (asyncMode) {\n" +
                   "       myTreeModel.nodesWereInserted(parentNode,new int[] {i} );\n" +
                   "    }";
    String str11 = "    '_parentNode.insert('_newNode, '_i);\n" +
                   "    if (asyncMode) {\n" +
                   "       myTreeModel.nodesWereInserted('_parentNode,new int[] {'_i} );\n" +
                   "    }";
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

    String str25 = "  LaterInvocator.invokeLater(new Runnable() {\n" +
                   "          public void run() {\n" +
                   "            LOG.info(\"refreshFilesAsync, modalityState=\" + ModalityState.current());\n" +
                   "            myHandler.getFiles().refreshFilesAsync(new Runnable() {\n" +
                   "              public void run() {\n" +
                   "                semaphore.up();\n" +
                   "              }\n" +
                   "            });\n" +
                   "          }\n" +
                   "        });";
    String str26 = "  LaterInvocator.invokeLater('_Params{1,10});";
    String str27 = "  com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater($Params$);";
    String expectedResult10 = "  com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new Runnable() {\n" +
                              "          public void run() {\n" +
                              "            LOG.info(\"refreshFilesAsync, modalityState=\" + ModalityState.current());\n" +
                              "            myHandler.getFiles().refreshFilesAsync(new Runnable() {\n" +
                              "              public void run() {\n" +
                              "                semaphore.up();\n" +
                              "              }\n" +
                              "            });\n" +
                              "          }\n" +
                              "        });";

    assertEquals("Anonymous in parameter", expectedResult10, replace(str25, str26, str27));

    String str28 = "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile,\n" +
                   "                                                          processedElement.getTextOffset(), true,\n" +
                   "                                                          !myUsageViewDescriptor.toMarkInvalidOrReadonlyUsages(), null);";
    String str29 = "new UTElementNode('_param, '_directory, '_null, '_0, '_true, !'_descr.toMarkInvalidOrReadonlyUsages(),\n" +
                   "  '_referencesWord)";
    String str30 = "new UTElementNode($param$, $directory$, $null$, $0$, $true$, true,\n" +
                   "  $referencesWord$)";

    String expectedResult11 = "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile, processedElement.getTextOffset(), true, true,\n" +
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

    String s37 = "try { \n" +
                 "  ParamChecker.isTrue(1==1, \"!!!\");\n  \n" +
                 "  // comment we want to leave\n  \n" +
                 "  ParamChecker.isTrue(2==2, \"!!!\");\n" +
                 "} catch(Exception ex) {}";
    String s38 = "try {\n" +
                 "  '_Statement{0,100};\n" +
                 "} catch(Exception ex) {}";
    String s39 = "$Statement$;";

    String expectedResult14 = "ParamChecker.isTrue(1==1, \"!!!\");\n  \n" +
                              "  // comment we want to leave\n  \n" +
                              "  ParamChecker.isTrue(2==2, \"!!!\");";
    assertEquals("remove try with comments inside", expectedResult14, replace(s37, s38, s39));

    String s40 = "ParamChecker.instanceOf(queryKey, GroupBySqlTypePolicy.GroupKey.class);";
    String s41 = "ParamChecker.instanceOf('_obj, '_class.class);";
    String s42 = "assert $obj$ instanceof $class$ : \"$obj$ is an instance of \" + $obj$.getClass() + \"; expected \" + $class$.class;";
    String expectedResult15 = "assert queryKey instanceof GroupBySqlTypePolicy.GroupKey : \"queryKey is an instance of \" + queryKey.getClass() + \"; expected \" + GroupBySqlTypePolicy.GroupKey.class;";

    assertEquals("Matching/replacing .class literals", expectedResult15, replace(s40, s41, s42));

    String s43 = "class Wpd {\n" +
                 "  static final String TAG_BEAN_VALUE = \"\";\n" +
                 "}\n" +
                 "class X {\n" +
                 "  XmlTag beanTag = rootTag.findSubTag(Wpd.TAG_BEAN_VALUE);\n" +
                 "}";
    String s44 = "'_Instance?.findSubTag( '_Parameter:[exprtype( *String ) ])";
    String s45 = "jetbrains.fabrique.util.XmlApiUtil.findSubTag($Instance$, $Parameter$)";
    String expectedResult16 = "class Wpd {\n" +
                              "  static final String TAG_BEAN_VALUE = \"\";\n" +
                              "}\n" +
                              "class X {\n" +
                              "  XmlTag beanTag = jetbrains.fabrique.util.XmlApiUtil.findSubTag(rootTag, Wpd.TAG_BEAN_VALUE);\n" +
                              "}";

    assertEquals("Matching/replacing static fields", expectedResult16, replace(s43, s44, s45, true));

    String s46 = "Rectangle2D rec = new Rectangle2D.Double(\n" +
                 "                drec.getX(),\n" +
                 "                drec.getY(),\n" +
                 "                drec.getWidth(),\n" +
                 "                drec.getWidth());";
    String s47 = "$Instance$.$MethodCall$()";
    String s48 = "OtherClass.round($Instance$.$MethodCall$(),5)";
    String expectedResult17 = "Rectangle2D rec = new Rectangle2D.Double(\n" +
                              "                OtherClass.round(drec.getX(),5),\n" +
                              "                OtherClass.round(drec.getY(),5),\n" +
                              "                OtherClass.round(drec.getWidth(),5),\n" +
                              "                OtherClass.round(drec.getWidth(),5));";
    assertEquals("Replace in constructor", expectedResult17, replace(s46, s47, s48));

    String s49 = "class A {}\n" +
                 "class B extends A {}\n" +
                 "class C {\n" +
                 "  A a = new B();\n" +
                 "}";
    String s50 = "A '_b = new '_B:*A ();";
    String s51 = "A $b$ = new $B$(\"$b$\");";
    String expectedResult18 = "class A {}\n" +
                              "class B extends A {}\n" +
                              "class C {\n" +
                              "  A a = new B(\"a\");\n" +
                              "}";

    assertEquals("Class navigation", expectedResult18, replace(s49, s50, s51, true));

    String s52 = "try {\n" +
                 "  aaa();\n" +
                 "} finally {\n" +
                 "  System.out.println();" +
                 "}\n" +
                 "try {\n" +
                 "  aaa2();\n" +
                 "} catch(Exception ex) {\n" +
                 "  aaa3();\n" +
                 "}\n" +
                 "finally {\n" +
                 "  System.out.println();\n" +
                 "}\n" +
                 "try {\n" +
                 "  aaa4();\n" +
                 "} catch(Exception ex) {\n" +
                 "  aaa5();\n" +
                 "}\n";
    String s53 = "try { '_a; } finally {\n" +
                 "  '_b;" +
                 "}";
    String s54 = "$a$;";
    String expectedResult19 = "aaa();\n" +
                              "try {\n" +
                              "  aaa2();\n" +
                              "} catch(Exception ex) {\n" +
                              "  aaa3();\n" +
                              "}\n" +
                              "finally {\n" +
                              "  System.out.println();\n" +
                              "}\n" +
                              "try {\n" +
                              "  aaa4();\n" +
                              "} catch(Exception ex) {\n" +
                              "  aaa5();\n" +
                              "}\n";

    options.getMatchOptions().setLooseMatching(false);
    try {
      assertEquals("Try/finally unwrapped with strict matching", expectedResult19, replace(s52, s53, s54));
    } finally {
      options.getMatchOptions().setLooseMatching(true);
    }

    String expectedResult19Loose = "aaa();\n" +
                                   "aaa2();\n" +
                                   "try {\n" +
                                   "  aaa4();\n" +
                                   "} catch(Exception ex) {\n" +
                                   "  aaa5();\n" +
                                   "}\n";
    assertEquals("Try/finally unwrapped with loose matching", expectedResult19Loose, replace(s52, s53, s54));


    String s55 = "for(Iterator<String> iterator = stringlist.iterator(); iterator.hasNext();) {\n" +
                 "      String str = iterator.next();\n" +
                 "      System.out.println( str );\n" +
                 "}";
    String s56 = "for (Iterator<$Type$> $variable$ = $container$.iterator(); $variable$.hasNext();) {\n" +
                 "    $Type$ $var$ = $variable$.next();\n" +
                 "    $Statements$;\n" +
                 "}";
    String s57 = "for($Type$ $var$:$container$) {\n" +
                 "  $Statements$;\n" +
                 "}";
    String expectedResult20 = "for(String str:stringlist) {\n" +
                              "  System.out.println( str );\n" +
                              "}";

    assertEquals("for with foreach", expectedResult20, replace(s55, s56, s57));

    String s58 = "class A {\n" +
                 "  static Set<String> b_MAP = new HashSet<String>();\n" +
                 "  int c;\n" +
                 "}";
    String s59 = "'a:[ regex( (.*)_MAP ) ]";
    String s60 = "$a_1$_SET";
    String expectedResult21 = "class A {\n" +
                              "  static Set<String> b_SET = new HashSet<String>();\n" +
                              "  int c;\n" +
                              "}";

    assertEquals("replace symbol in definition", expectedResult21, replace(s58, s59, s60));

    String s64 = "int x = 42;\n" +
                 "int y = 42; // Stuff";
    String s65 = "'_Type '_Variable = '_Value; // '_Comment";
    String s66 = "/**\n" +
                 " *$Comment$\n" +
                 " */\n" +
                 "$Type$ $Variable$ = $Value$;";
    String expectedResult23 = "int x = 42;\n" +
                              "/**\n" +
                              " * Stuff\n" +
                              " */\n" +
                              "int y = 42;";

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
  }

  public void testReplaceParameter() {
    String in1 = "class A {\n" +
                 "  void b(int c,\n" +
                 "         int d, /*1*/ int e) {}\n" +
                 "}";

    String expected1a = "class A {\n" +
                        "  void b(int c,\n" +
                        "         int d2, /*1*/ int e) {}\n" +
                        "}";
    assertEquals("replace method parameter", expected1a, replace(in1, "int d;", "int d2;"));

    String expected1b = "class A {\n" +
                        "  void b(int /*!*/ c,\n" +
                        "         int /*!*/ d, /*1*/ int /*!*/ e) {}\n" +
                        "}";
    assertEquals(expected1b, replace(in1, "void b('_T '_v*);", "void b($T$ /*!*/ $v$);"));

    String expected1c = "class A {\n" +
                        "  void /**/ b(int c,\n" +
                        "         int d, /*1*/ int e) {}\n" +
                        "}";
    assertEquals("replace multi match parameter", expected1c, replace(in1, "void b(int '_x*);", "void /**/ b(int $x$);"));

    String expected1d = "class A {\n" +
                        "  void b(int c,\n" +
                        "         int d, /*1*/ int e) {}\n" +
                        "void c(int c,\n" +
                        "         int d, /*1*/ int e) {}\n" +
                        "}";
    assertEquals("replace multiple occurrences of the same variable", expected1d, replace(in1, "void b('_T '_p*);", "void b($T$ $p$);\n" +
                                                                                                                    "void c($T$ $p$) {}"));

    String in2 = "class X {" +
                 "  void x() {}" +
                 "}";
    String expected2 = "class X {" +
                       "  void /**/ x() {}" +
                       "}";
    assertEquals("replace no match parameter", expected2, replace(in2, "void x(int '_a*);", "void /**/ x() {}"));

    String in3 = "class X {" +
                 "  void x(String s, Integer i) {}" +
                 "}";
    String expected3 = "class X {" +
                       "  void x(List<String> /*>*/ s, List<Integer> /*>*/ i) {}" +
                       "}";
    assertEquals(expected3, replace(in3, "void x('_T '_v*);", "void x(List<$T$> /*>*/ $v$);"));

    String in4 = "class X {" +
                 "  void a(Map<String, Integer> b, Map<String, Integer> c) {}" +
                 "}";
    String expected4 = "class X {" +
                       "  void a(Map<String, Integer> /*!*/ b, Map<, > /*!*/ c) {}" + // todo fix replacement of second parameter type
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
  }

  public void testSeveralStatements() {
    String s1 = "{\n" +
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n" +
                "      }\n" +
                "{\n" +
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n" +
                "      }\n" +
                "{\n" +
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n" +
                "      }";
    String s2 =
                "        System.out.println(1);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(3);\n";
    String s3 = "        System.out.println(3);\n" +
                "        System.out.println(2);\n" +
                "        System.out.println(1);\n";
    String expectedResult1 = "{\n" +
                             "    System.out.println(3);\n" +
                             "    System.out.println(2);\n" +
                             "    System.out.println(1);\n" +
                             "}\n" +
                             "{\n" +
                             "    System.out.println(3);\n" +
                             "    System.out.println(2);\n" +
                             "    System.out.println(1);\n" +
                             "}\n" +
                             "{\n" +
                             "    System.out.println(3);\n" +
                             "    System.out.println(2);\n" +
                             "    System.out.println(1);\n" +
                             "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("three statements replacement", expectedResult1, replace(s1, s2, s3));
    options.setToReformatAccordingToStyle(false);

    String s4 = "ProgressManager.getInstance().startNonCancelableAction();\n" +
                "    try {\n" +
                "      read(id, READ_PARENT);\n" +
                "      return myViewport.parent;\n" +
                "    }\n" +
                "    finally {\n" +
                "      ProgressManager.getInstance().finishNonCancelableAction();\n" +
                "    }";
    String s5 = "ProgressManager.getInstance().startNonCancelableAction();\n" +
                "    try {\n" +
                "      '_statement{2,2};\n" +
                "    }\n" +
                "    finally {\n" +
                "      ProgressManager.getInstance().finishNonCancelableAction();\n" +
                "    }";
    String s6 = "$statement$;";
    String expectedResult2 = "read(id, READ_PARENT);\n" +
                             "      return myViewport.parent;";
    assertEquals("extra ;", expectedResult2, replace(s4, s5, s6));

    String s7 = "public class A {\n" +
                "    void f() {\n" +
                "        new Runnable() {\n" +
                "            public void run() {\n" +
                "                l();\n" +
                "            }\n" +
                "\n" +
                "            private void l() {\n" +
                "                int i = 9;\n" +
                "                int j = 9;\n" +
                "            }\n" +
                "        };\n" +
                "        new Runnable() {\n" +
                "            public void run() {\n" +
                "                l();\n" +
                "            }\n" +
                "\n" +
                "            private void l() {\n" +
                "                l();\n" +
                "                l();\n" +
                "            }\n" +
                "        };\n" +
                "    }\n" +
                "\n" +
                "}";
    String s8 = "new Runnable() {\n" +
                "    public void run() {\n" +
                "        '_l ();\n" +
                "    }\n" +
                "    private void '_l () {\n" +
                "        '_st{2,2};\n" +
                "    }\n" +
                "};";
    String s9 = "new My() {\n" +
                "    public void f() {\n" +
                "        $st$;\n" +
                "    }\n" +
                "};";

    String expectedResult3 = "public class A {\n" +
                             "    void f() {\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                int i = 9;\n" +
                             "                int j = 9;\n" +
                             "            }\n" +
                             "        };\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "        };\n" +
                             "    }\n" +
                             "\n" +
                             "}";
    boolean formatAccordingToStyle = options.isToReformatAccordingToStyle();
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; 2", expectedResult3, replace(s7, s8, s9));

    String s10 = "public class A {\n" +
                 "    void f() {\n" +
                 "        new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "\n" +
                 "        };\n" +
                 "        new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "\n" +
                 "        };\n" +
                 "new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                l();\n" +
                 "                l();\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                l2();\n" +
                 "                l2();\n" +
                 "            }\n" +
                 "\n" +
                 "        };\n" +
                 "    }\n" +
                 "\n" +
                 "    private void l() {\n" +
                 "        int i = 9;\n" +
                 "        int j = 9;\n" +
                 "    }\n" +
                 "}\n" +
                 "\n" +
                 "abstract class My {\n" +
                 "    abstract void f();\n" +
                 "}";
    String s11 = "new Runnable() {\n" +
                 "            public void run() {\n" +
                 "                '_l{2,2};\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                '_l;\n" +
                 "            }\n" +
                 "\n" +
                 "        };";
    String s12 = "new My() {\n" +
                 "            public void f() {\n" +
                 "                $l$;\n" +
                 "            }\n" +
                 "        };";
    String expectedResult4 = "public class A {\n" +
                             "    void f() {\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "        };\n" +
                             "        new My() {\n" +
                             "            public void f() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "        };\n" +
                             "        new Runnable() {\n" +
                             "            public void run() {\n" +
                             "                l();\n" +
                             "                l();\n" +
                             "            }\n" +
                             "\n" +
                             "            public void run2() {\n" +
                             "                l2();\n" +
                             "                l2();\n" +
                             "            }\n" +
                             "\n" +
                             "        };\n" +
                             "    }\n" +
                             "\n" +
                             "    private void l() {\n" +
                             "        int i = 9;\n" +
                             "        int j = 9;\n" +
                             "    }\n" +
                             "}\n" +
                             "\n" +
                             "abstract class My {\n" +
                             "    abstract void f();\n" +
                             "}";

    assertEquals("same multiple occurrences 2 times", expectedResult4, replace(s10, s11, s12));

    options.setToReformatAccordingToStyle(formatAccordingToStyle);

    String s13 = "    PsiLock.LOCK.acquire();\n" +
                 "    try {\n" +
                 "      return value;\n" +
                 "    }\n" +
                 "    finally {\n" +
                 "      PsiLock.LOCK.release();\n" +
                 "    }";
    String s13_2 = "    PsiLock.LOCK.acquire();\n" +
                   "    try {\n" +
                   "      if (true) { return value; }\n" +
                   "    }\n" +
                   "    finally {\n" +
                   "      PsiLock.LOCK.release();\n" +
                   "    }";
    String s13_3 = "    PsiLock.LOCK.acquire();\n" +
                   "    try {\n" +
                   "      if (true) { return value; }\n\n" +
                   "      if (true) { return value; }\n" +
                   "    }\n" +
                   "    finally {\n" +
                   "      PsiLock.LOCK.release();\n" +
                   "    }";
    String s14 = "    PsiLock.LOCK.acquire();\n" +
                 "    try {\n" +
                 "      '_T{1,1000};\n" +
                 "    }\n" +
                 "    finally {\n" +
                 "      PsiLock.LOCK.release();\n" +
                 "    }";
    String s15 = "synchronized(PsiLock.LOCK) {\n" +
                 "  $T$;\n" +
                 "}";

    String expectedResult5 = "synchronized (PsiLock.LOCK) {\n" +
                             "    return value;\n" +
                             "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; over return", expectedResult5, replace(s13, s14, s15));
    options.setToReformatAccordingToStyle(false);


    String expectedResult6 = "synchronized (PsiLock.LOCK) {\n" +
                             "    if (true) {\n" +
                             "        return value;\n" +
                             "    }\n" +
                             "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; over if", expectedResult6, replace(s13_2, s14, s15));
    options.setToReformatAccordingToStyle(false);


    String expectedResult7 = "synchronized (PsiLock.LOCK) {\n" +
                             "    if (true) {\n" +
                             "        return value;\n" +
                             "    }\n" +
                             "\n" +
                             "    if (true) {\n" +
                             "        return value;\n" +
                             "    }\n" +
                             "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("newlines in matches of several lines", expectedResult7, replace(s13_3, s14, s15));
    options.setToReformatAccordingToStyle(false);

    String s16 = "public class SSTest {\n" +
                 "  Object lock;\n" +
                 "  public Object getProducts (String[] productNames) {\n" +
                 "    synchronized (lock) {\n" +
                 "      Object o = new Object ();\n" +
                 "      assert o != null;\n" +
                 "      return o;\n" +
                 "    }\n" +
                 "  }\n" +
                 "}";
    String s16_2 = "public class SSTest {\n" +
                   "  Object lock;\n" +
                   "  public void getProducts (String[] productNames) {\n" +
                   "    synchronized (lock) {\n" +
                   "      boolean[] v = {true};\n" +
                   "    }\n" +
                   "  }\n" +
                   "}";

    String s17 = "synchronized(lock) {\n" +
                 "  '_Statement*;\n" +
                 "}";

    String s18 = "$Statement$;";
    String expectedResult8 = "public class SSTest {\n" +
                             "  Object lock;\n" +
                             "  public Object getProducts (String[] productNames) {\n" +
                             "    Object o = new Object ();\n" +
                             "      assert o != null;\n" +
                             "      return o;\n" +
                             "  }\n" +
                             "}";
    String expectedResult8_2 = "public class SSTest {\n" +
                               "  Object lock;\n" +
                               "  public void getProducts (String[] productNames) {\n" +
                               "    boolean[] v = {true};\n" +
                               "  }\n" +
                               "}";

    assertEquals("extra ;", expectedResult8, replace(s16, s17, s18));

    assertEquals("missed ;", expectedResult8_2, replace(s16_2, s17, s18));
  }

  public void testSpecialClassReplacement() {
    String in = "enum Color {\n" +
                "  RED, GREEN, BLUE\n" +
                "}\n" +
                "interface X {\n" +
                "  void x();\n" +
                "}\n" +
                "@interface Anno {}\n";
    String what = "class 'X {}";
    String by = "/** @author me */\n" +
                "class $X$ {}";
    String expected = "/** @author me */\n" +
                      "enum Color {\n" +
                      "  RED, GREEN, BLUE\n" +
                      "}\n" +
                      "/** @author me */\n" +
                      "interface X {\n" +
                      "  void x();\n" +
                      "}\n" +
                      "/** @author me */\n" +
                      "@interface Anno {}\n";
    assertEquals("Special class replacement", expected, replace(in, what, by, true));

    String in2 = "new ArrayList<String>(null) {\n" +
                 "  @Override\n" +
                 "  public int hashCode() {\n" +
                 "    return super.hashCode();\n" +
                 "  }\n" +
                 "}";
    String by2 = "class $X$ {\n" +
                "  public String toString() {\n" +
                "    return \"hello\";\n" +
                "  }\n" +
                "}\n";
    String expected2 = "new ArrayList<String>(null){\n" +
                       "  public String toString() {\n" +
                       "    return \"hello\";\n" +
                       "  }\n" +
                       "  @Override\n" +
                       "  public int hashCode() {\n" +
                       "    return super.hashCode();\n" +
                       "  }\n" +
                       "}";
    assertEquals("Special anonymous class replacement", expected2, replace(in2, what, by2, false));
    assertTrue(true);
  }

  public void testClassReplacement() {
    options.setToReformatAccordingToStyle(true);

    String s1 = "class A { public void b() {} }";
    String s2 = "class 'a { '_Other* }";
    String s3 = "class $a$New { Logger LOG; $Other$ }";
    String expectedResult = "class ANew {\n" +
                            "    Logger LOG;\n" +
                            "\n" +
                            "    public void b() {\n" +
                            "    }\n" +
                            "}";
    assertEquals("Basic class replacement", expectedResult, replace(s1, s2, s3, true));

    String s4 = "class A { class C {} public void b() {} int f; }";
    String s5 = "class 'a { '_Other* }";
    String s6 = "class $a$ { Logger LOG; $Other$ }";
    String expectedResult2 = "class A {\n" +
                             "    Logger LOG;\n" +
                             "\n" +
                             "    class C {\n" +
                             "    }\n" +
                             "\n" +
                             "    public void b() {\n" +
                             "    }\n" +
                             "\n" +
                             "    int f;\n" +
                             "}";

    assertEquals("Order of members in class replacement", expectedResult2, replace(s4, s5, s6, true));

    String s7 = "class A extends B { int c; void b() {} { a = 1; } }";
    String s8 = "class 'A extends B { '_Other* }";
    String s9 = "class $A$ extends B2 { $Other$ }";
    String expectedResult3 = "class A extends B2 {\n" +
                             "    int c;\n" +
                             "\n" +
                             "    void b() {\n" +
                             "    }\n" +
                             "\n" +
                             "    {\n" +
                             "        a = 1;\n" +
                             "    }\n" +
                             "}";

    assertEquals("Unsupported pattern exception", expectedResult3, replace(s7, s8, s9, true));

    String s10 = "/** @example */\n" +
                 "class A {\n" +
                 "  class C {}\n" +
                 "  public void b() {}\n" +
                 "  int f;\n" +
                 "}";
    String s11 = "class 'a { '_Other* }";
    String s12 = "public class $a$ {\n" +
                 "  $Other$\n" +
                 "}";
    String expectedResult4 = "/**\n" +
                             " * @example\n" +
                             " */\n" +
                             "public class A {\n" +
                             "    class C {\n" +
                             "    }\n" +
                             "\n" +
                             "    public void b() {\n" +
                             "    }\n" +
                             "\n" +
                             "    int f;\n" +
                             "}";

    options.setToReformatAccordingToStyle(true);
    assertEquals("Make class public", expectedResult4, replace(s10, s11, s12, true));
    options.setToReformatAccordingToStyle(false);

    String s13 = "class CustomThread extends Thread {\n" +
                 "public CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {\n" +
                 "    super(CustomThreadGroup.getThreadGroup(), \"CustomThread\");\n" +
                 "    setDaemon(true);\n" +
                 "    if (in instanceof BufferedInputStream) {\n" +
                 "        bis = (BufferedInputStream)in;\n" +
                 "    } else {\n" +
                 "    bis = new BufferedInputStream(in);\n" +
                 "    }\n" +
                 "    this.out = out;\n" +
                 "    this.closeOutOnExit = closeOutOnExit;\n" +
                 "}\n" +
                 "}";
    String s14 = "class '_Class extends Thread {\n" +
                 "  '_Class('_ParameterType '_ParameterName*) {\n" +
                 "\t  super (CustomThreadGroup.getThreadGroup(), '_superarg* );\n" +
                 "    '_Statement*;\n" +
                 "  }\n" +
                 "}";
    String s15 = "class $Class$ extends CustomThread {\n" +
                 "  $Class$($ParameterType$ $ParameterName$) {\n" +
                 "\t  super($superarg$);\n" +
                 "    $Statement$;\n" +
                 "  }\n" +
                 "}";

    String expectedResult5 = "class CustomThread extends CustomThread {\n" +
                             "    CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {\n" +
                             "        super(\"CustomThread\");\n" +
                             "        setDaemon(true);\n" +
                             "        if (in instanceof BufferedInputStream) {\n" +
                             "            bis = (BufferedInputStream) in;\n" +
                             "        } else {\n" +
                             "            bis = new BufferedInputStream(in);\n" +
                             "        }\n" +
                             "        this.out = out;\n" +
                             "        this.closeOutOnExit = closeOutOnExit;\n" +
                             "    }\n" +
                             "}";
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

    String s25 = "class A {\n" +
                 "  // comment before\n" +
                 "  protected short a; //  comment after\n" +
                 "}";
    String s26 = "short a;";
    String s27 = "Object a;";
    String expectedResult10 = "class A {\n" +
                              "  // comment before\n" +
                              "  protected Object a; //  comment after\n" +
                              "}";

    assertEquals("Replacing dcl with saving access modifiers", expectedResult10, replace(s25, s26, s27));

    String s28 = "aaa";
    String s29 = "class 'Class {\n" +
                 " 'Class('_ParameterType '_ParameterName) {\n" +
                 "    'Class('_ParameterName);\n" +
                 "  }\n" +
                 "}";
    String s30 = "class $Class$ {\n" +
                 "  $Class$($ParameterType$ $ParameterName$) {\n" +
                 "     this($ParameterName$);\n" +
                 "  }\n" +
                 "}";
    String expectedResult11 = "aaa";

    assertEquals("Complex class replacement", expectedResult11, replace(s28, s29, s30));

    String s31 = "class A {\n" +
                 "  int a; // comment\n" +
                 "  char b;\n" +
                 "  int c; // comment2\n" +
                 "}";

    String s32 = "'_Type 'Variable = '_Value?; //'_Comment";
    String s33 = "/**$Comment$*/\n" +
                 "$Type$ $Variable$ = $Value$;";

    String expectedResult12 = "class A {\n" +
                              "    /**\n" +
                              "     * comment\n" +
                              "     */\n" +
                              "    int a;\n" +
                              "    char b;\n" +
                              "    /**\n" +
                              "     * comment2\n" +
                              "     */\n" +
                              "    int c;\n" +
                              "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Replacing comments with javadoc for fields", expectedResult12, replace(s31, s32, s33, true));
    options.setToReformatAccordingToStyle(false);

    String s34 = "/**\n" +
                 " * This interface stores XXX\n" +
                 " * <p/>\n" +
                 " */\n" +
                 "public interface X {\n" +
                 "    public static final String HEADER = Headers.HEADER;\n" +
                 "\n" +
                 "}";

    String s35 = "public interface '_MessageInterface {\n" +
                 "    public static final String '_X = '_VALUE;\n" +
                 "    '_blah*" +
                 "}";
    String s36 = "public interface $MessageInterface$ {\n" +
                 "    public static final String HEADER = $VALUE$;\n" +
                 "    $blah$\n" +
                 "}";

    String expectedResult13 = "/**\n" +
                              " * This interface stores XXX\n" +
                              " * <p/>\n" +
                              " */\n" +
                              "public interface X {\n" +
                              "    public static final String HEADER = Headers.HEADER;\n" +
                              "    \n" +
                              "}";

    assertEquals("Replacing interface with interface, saving comments properly", expectedResult13, replace(s34, s35, s36, true));
  }

  @SuppressWarnings("unused")
  public void _testClassReplacement3() {
    String s37 = "class A { int a = 1; void B() {} int C(char ch) { int z = 1; } int b = 2; }";

    String s38 = "class 'A { '_T '_M*('_PT '_PN*) { '_S*; } '_O* }";
    String s39 = "class $A$ { $T$ $M$($PT$ $PN$) { System.out.println(\"$M$\"); $S$; } $O$ }";

    String expectedResult14 = "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";
    String expectedResult14_2 = "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";

    assertEquals("Multiple methods replacement", expectedResult14, replace(s37, s38, s39, true)
    );
  }

  public void testClassReplacement4() {
    String s1 = "class A {\n" +
                "  int a = 1;\n" +
                "  int b;\n" +
                "  private int c = 2;\n" +
                "}";

    String s2 = "@Modifier(\"packageLocal\") '_Type '_Instance = '_Init?;";
    String s3 = "public $Type$ $Instance$ = $Init$;";

    String expectedResult = "class A {\n" +
                            "  public int a = 1;\n" +
                            "  public int b;\n" +
                            "  private int c = 2;\n" +
                            "}";

    assertEquals("Multiple fields replacement", expectedResult, replace(s1, s2, s3, true));
  }

  public void testClassReplacement5() {
    String s1 = "public class X {\n" +
                "    /**\n" +
                "     * zzz\n" +
                "     */\n" +
                "    void f() {\n" +
                "\n" +
                "    }\n" +
                "}";

    String s2 = "class 'c {\n" +
                "    /**\n" +
                "     * zzz\n" +
                "     */\n" +
                "    void f(){}\n" +
                "}";
    String s3 = "class $c$ {\n" +
                "    /**\n" +
                "     * ppp\n" +
                "     */\n" +
                "    void f(){}\n" +
                "}";

    String expectedResult = "public class X {\n" +
                            "    /**\n" +
                            "     * ppp\n" +
                            "     */\n" +
                            "    void f(){}\n" +
                            "}";

    assertEquals("Not preserving comment if it is present", expectedResult, replace(s1, s2, s3, true));
  }

  public void testClassReplacement6() {
    String s1 = "public class X {\n" +
                "   /**\n" +
                "    * zzz\n" +
                "    */\n" +
                "   private void f(int i) {\n" +
                "       //s\n" +
                "   }\n" +
                "}";

    String s2 = "class 'c {\n" +
                "   /**\n" +
                "    * zzz\n" +
                "    */\n" +
                "   void f('_t '_p){'_s+;}\n" +
                "}";
    String s3 = "class $c$ {\n" +
                "   /**\n" +
                "    * ppp\n" +
                "    */\n" +
                "   void f($t$ $p$){$s$;}\n" +
                "}";

    String expectedResult = "public class X {\n" +
                            "   /**\n" +
                            "    * ppp\n" +
                            "    */\n" +
                            "   private void f(int i){//s\n" +
                            "}\n" +
                            "}";

    assertEquals("Correct class replacement", expectedResult, replace(s1, s2, s3));

    String s1_2 = "public class X {\n" +
                  "   /**\n" +
                  "    * zzz\n" +
                  "    */\n" +
                  "   private void f(int i) {\n" +
                  "       int a = 1;\n" +
                  "       //s\n" +
                  "   }\n" +
                  "}";
    String expectedResult2 = "public class X {\n" +
                            "   /**\n" +
                            "    * ppp\n" +
                            "    */\n" +
                            "   private void f(int i){int a = 1;\n" +
                            "       //s\n" +
                            "}\n" +
                            "}";

    assertEquals("Correct class replacement, 2", expectedResult2, replace(s1_2, s2, s3));
  }

  public void testClassReplacement7() {
    String s1 = "/**\n" +
                "* Created by IntelliJ IDEA.\n" +
                "* User: cdr\n" +
                "* Date: Nov 15, 2005\n" +
                "* Time: 4:23:29 PM\n" +
                "* To change this template use File | Settings | File Templates.\n" +
                "*/\n" +
                "public class CC {\n" +
                "  /** My Comment */ int a = 3; // aaa\n" +
                "  // bbb\n" +
                "  long c = 2;\n" +
                "  void f() {\n" +
                "  }\n" +
                "}";
    String s2 = "/**\n" +
                "* Created by IntelliJ IDEA.\n" +
                "* User: '_USER\n" +
                "* Date: '_DATE\n" +
                "* Time: '_TIME\n" +
                "* To change this template use File | Settings | File Templates.\n" +
                "*/\n" +
                "class 'c {\n" +
                "  '_other*\n" +
                "}";
    String s3 = "/**\n" +
                "* by: $USER$\n" +
                "*/\n" +
                "class $c$ {\n" +
                "  $other$\n" +
                "}";
    String expectedResult = "/**\n" +
                            "* by: cdr\n" +
                            "*/\n" +
                            "public class CC {\n" +
                            "  /** My Comment */ int a = 3; // aaa\n" +
                            "  // bbb\n" +
                            "  long c = 2;\n" +
                            "  void f() {\n" +
                            "  }\n" +
                            "}";

    assertEquals("Class with comment replacement", expectedResult, replace(s1, s2, s3, true));
  }

  public void testClassReplacement8() {
    String s1 = "public class CC {\n" +
                "   /** AAA*/ int b = 1; // comment\n" +
                "}";
    String s2 = "int b = 1;";
    String s3 = "long c = 2;";
    String expectedResult = "public class CC {\n" +
                            "   /** AAA*/ long c = 2; // comment\n" +
                            "}";

    assertEquals("Class field replacement with simple pattern", expectedResult, replace(s1, s2, s3, true));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/";
  }

  public void testClassReplacement9() throws IOException {
    String s1 = loadFile("before1.java");
    String s2 = "class 'A extends '_TestCaseCass:[regex( .*TestCase ) ] {\n" +
                "  '_OtherStatement*;\n" +
                "  public void '_testMethod*:[regex( test.* )] () {\n" +
                "  }\n" +
                "  '_OtherStatement2*;\n" +
                "}";
    String s3 = "class $A$ extends $TestCaseCass$ {\n" +
                "    $OtherStatement$;\n" +
                "    $OtherStatement2$;\n" +
                "}";
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
    String s2 = "class '_Class {\n" +
                "  '_ReturnType '_MethodName+('_ParameterType '_Parameter*){\n" +
                "    '_content*;\n" +
                "  }\n" +
                "  '_remainingclass*" +
                "}";
    String s3 = "class $Class$ {\n" +
                "  $remainingclass$\n" +
                "  @Override $ReturnType$ $MethodName$($ParameterType$ $Parameter$){\n" +
                "    $content$;\n" +
                "  }\n" +
                "}";
    String expectedResult = loadFile("after2.java");

    options.setToReformatAccordingToStyle(true);
    assertEquals("Class replacement 10", expectedResult, replace(s1, s2, s3, true));
  }

  public void testCatchReplacement() {
    String s1 = "try {\n" +
                "  aaa();\n" +
                "} catch(Exception ex) {\n" +
                "  LOG.assertTrue(false);\n" +
                "}";
    String s2 = "{  LOG.assertTrue(false); }";
    String s3 = "{  if (false) LOG.assertTrue(false); }";
    String expectedResult = "try {\n" +
                "  aaa();\n" +
                "} catch (Exception ex) {\n" +
                "    if (false) LOG.assertTrue(false);\n" +
                "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Catch replacement by block", expectedResult, replace(s1, s2, s3));
    options.setToReformatAccordingToStyle(false);

  }

  public void testSavingAccessModifiersDuringClassReplacement() {
    String s43 = "public @Deprecated class Foo implements Comparable<Foo> {\n" +
                 "  int x;\n" +
                 "  void m(){}\n" +
                 "}";
    String s44 = "class 'Class implements '_Interface { '_Content* }";
    String s45 = "@MyAnnotation\n" +
                 "class $Class$ implements $Interface$ {\n" +
                 "  $Content$\n" +
                 "}";
    String expectedResult16 = "@MyAnnotation public @Deprecated\n" +
                              "class Foo implements Comparable<Foo> {\n" +
                              "  int x;\n" +
                              "  void m(){}\n" +
                              "}";

    assertEquals(
      "Preserving var modifiers and generic information in type during replacement",
      expectedResult16,
      replace(s43, s44, s45, true)
    );

    String in1 = "public class A {" +
                 "  public class B {}" +
                 "}";
    String what1 = "class '_A {" +
                   "  class '_B {}" +
                   "}";
    String by1 = "class $A$ {" +
                 "  private class $B$ {}" +
                 "}";
    String expected1 = "public class A {  private class B {}}";
    assertEquals("No illegal modifier combinations during replacement", expected1,
                 replace(in1, what1, by1));
  }

  public void testDontRequireSpecialVarsForUnmatchedContent() {

    String s43 = "public @Deprecated class Foo implements Comparable<Foo> {\n" +
                 "  int x;\n" +
                 "  void m(){}\n" +
                 " }";
    String s44 = "class 'Class implements '_Interface {}";
    String s45 = "@MyAnnotation\n" +
                 "class $Class$ implements $Interface$ {}";
    String expectedResult16 = "@MyAnnotation public @Deprecated\n" +
                              "class Foo implements Comparable<Foo> {\n" +
                              "  int x;\n" +
                              "  void m(){}\n" +
                              " }";

    assertEquals(
      "Preserving class modifiers and generic information in type during replacement",
      expectedResult16,
      replace(s43, s44, s45, true)
    );

    String in = "public class A {\n" +
                "  int i,j, k;\n" +
                "  void m1() {}\n" +
                "\n" +
                "  public void m2() {}\n" +
                "  void m3() {}\n" +
                "}";
    String what = "class '_A {\n" +
                  "  public void '_m();\n" +
                  "}";
    String by = "class $A$ {\n" +
                "\tprivate void $m$() {}\n" +
                "}";
    assertEquals("Should keep member order when replacing",
                 "public class A {\n" +
                 "  int i ,j , k;\n" +
                 "  void m1() {}\n" +
                 "\n" +
                 "  private void m2() {}\n" +
                 "  void m3() {}\n" +
                 "}",
                 replace(in, what, by));
  }

  public void testClassReplacement2() {
    String s40 = "class A {\n" +
                 "  /* special comment*/\n" +
                 "  private List<String> a = new ArrayList();\n" +
                 "  static {\n" +
                 "    int a = 1;" +
                 "  }\n" +
                 "}";
    String s41 = "class '_Class {\n" +
                 "  '_Stuff2*\n" +
                 "  '_FieldType '_FieldName = '_Init?;\n" +
                 "  static {\n" +
                 "    '_Stmt*;\n" +
                 "  }\n" +
                 "  '_Stuff*\n" +
                 "}";
    String s42 = "class $Class$ {\n" +
                 "  $Stuff2$\n" +
                 "  $FieldType$ $FieldName$ = build$FieldName$Map();\n" +
                 "  private static $FieldType$ build$FieldName$Map() {\n" +
                 "    $FieldType$ $FieldName$ = $Init$;\n" +
                 "    $Stmt$;\n" +
                 "    return $FieldName$;\n" +
                 "  }\n" +
                 "  $Stuff$\n" +
                 "}";
    String expectedResult15 = "class A {\n" +
                              "  \n" +
                              "  /* special comment*/\n" +
                              "  private List<String> a = buildaMap();\n" +
                              "  private static List<String> buildaMap() {\n" +
                              "    List<String> a = new ArrayList();\n" +
                              "    int a = 1;\n" +
                              "    return a;\n" +
                              "  }\n" +
                              "  \n" +
                              "}";

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
    } catch (MalformedPatternException ignored) {}

    String s4 = "a=a;";
    String s5 = "a=a;";
    String s6 = "a=a";

    try {
      replace(s4, s5, s6);
      fail("Undefined no ; in replace");
    } catch (UnsupportedPatternException ignored) {}

    try {
      replace(s4, s6, s5);
      fail("Undefined no ; in search");
    } catch (UnsupportedPatternException ignored) {}
  }

  public void testActualParameterReplacementInConstructorInvokation() {
    String s1 = "filterActions[0] = new Action(TEXT,\n" +
                "    LifeUtil.getIcon(\"search\")) {\n" +
                "        void test() {\n" +
                "            int a = 1;\n" +
                "        }\n" +
                "};";
    String s2 = "LifeUtil.getIcon(\"search\")";
    String s3 = "StdIcons.SEARCH_LIFE";
    String expectedResult = "filterActions[0] = new Action(TEXT,\n" +
                "        StdIcons.SEARCH_LIFE) {\n" +
                "        void test() {\n" +
                "            int a = 1;\n" +
                "        }\n" +
                "};";
    options.setToReformatAccordingToStyle(true);
    options.setToShortenFQN(true);

    assertEquals("Replace in anonymous class parameter", expectedResult, replace(s1, s2, s3));
    options.setToShortenFQN(false);
    options.setToReformatAccordingToStyle(false);
  }

  public void testRemove() {
    String s1 = "class A {\n" +
                "  /* */\n" +
                "  void a() {\n" +
                "  }\n" +
                "  /*\n" +
                "  */\n" +
                "  int b = 1;\n" +
                "  /*\n" +
                "   *\n" +
                "   */\n" +
                "   class C {}\n" +
                "  {\n" +
                "    /* aaa */\n" +
                "    int a;\n" +
                "    /* */\n" +
                "    a = 1;\n" +
                "  }\n" +
                "}";
    String s2 = "/* 'a:[regex( .* )] */";
    String s2_2 = "/* */";
    String s3 = "";
    String expectedResult = "class A {\n" +
                            "    void a() {\n" +
                            "    }\n" +
                            "\n" +
                            "    int b = 1;\n" +
                            "\n" +
                            "    class C {\n" +
                            "    }\n" +
                            "\n" +
                            "    {\n" +
                            "        int a;\n" +
                            "        a = 1;\n" +
                            "    }\n" +
                            "}";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Removing comments", expectedResult, replace(s1, s2, s3));
    options.setToReformatAccordingToStyle(false);


    String expectedResult2 = "class A {\n" +
                             "  void a() {\n" +
                             "  }\n" +
                             "  int b = 1;\n" +
                             "  /*\n" +
                             "   *\n" +
                             "   */\n" +
                             "   class C {}\n" +
                             "  {\n" +
                             "    /* aaa */\n" +
                             "    int a;\n" +
                             "    a = 1;\n" +
                             "  }\n" +
                             "}";

    assertEquals("Removing comments", expectedResult2, replace(s1, s2_2, s3));
  }

  public void testTryCatchInLoop() {
    String code = "for (int i = 0; i < MIMEHelper.MIME_MAP.length; i++)\n" +
                "{\n" +
                "  String s = aFileNameWithOutExtention + MIMEHelper.MIME_MAP[i][0][0];\n" +
                "  try\n" +
                "  {\n" +
                "    if (ENABLE_Z107_READING)\n" +
                "    { in = aFileNameWithOutExtention.getClass().getResourceAsStream(s); }\n" +
                "    else\n" +
                "    { data = ResourceHelper.readResource(s); }\n" +
                "    mime = MIMEHelper.MIME_MAP[i][1][0];\n" +
                "    break;\n" +
                "  }\n" +
                "  catch (final Exception e)\n" +
                "  { continue; }\n" +
                "}";
    String toFind = "try { '_TryStatement*; } catch(Exception '_ExceptionDcl) { '_CatchStatement*; }";
    String replacement = "try { $TryStatement$; }\n" + "catch(Throwable $ExceptionDcl$) { $CatchStatement$; }";
    String expectedResult = "for (int i = 0; i < MIMEHelper.MIME_MAP.length; i++)\n" +
                            "{\n" +
                            "  String s = aFileNameWithOutExtention + MIMEHelper.MIME_MAP[i][0][0];\n" +
                            "  try { if (ENABLE_Z107_READING)\n" +
                            "    { in = aFileNameWithOutExtention.getClass().getResourceAsStream(s); }\n" +
                            "    else\n" +
                            "    { data = ResourceHelper.readResource(s); }\n" +
                            "    mime = MIMEHelper.MIME_MAP[i][1][0];\n" +
                            "    break; }\n" +
                            "catch(final Throwable e) { continue; }\n" +
                            "}";

    assertEquals("Replacing try/catch in loop", expectedResult, replace(code, toFind, replacement));
  }

  public void testUseStaticImport() {
    final String in = "class X {{ Math.abs(-1); }}";
    final String what = "Math.abs('_a)";
    final String by = "Math.abs($a$)";
    options.setToUseStaticImport(true);
    final String expected = "import static java.lang.Math.abs;\n" +
                            "\n" +
                            "class X {{ abs(-1); }}";
    assertEquals("Replacing with static import", expected, replace(in, what, by, true, true));

    final String in2 = "class X { void m(java.util.Random r) { Math.abs(r.nextInt()); }}";
    final String expected2 = "import static java.lang.Math.abs;\n" +
                             "\n" +
                             "class X { void m(java.util.Random r) { abs(r.nextInt()); }}";
    assertEquals("don't add broken static imports", expected2, replace(in2, what, by, true, true));

    final String by2 = "new java.util.Map.Entry() {}";
    final String expected3 = "import static java.util.Map.Entry;\n" +
                             "\n" +
                             "class X {{ new Entry() {}; }}";
    assertEquals("", expected3, replace(in, what, by2, true, true));

    final String in3 = "import java.util.Collections;" +
                       "class X {" +
                       "  void m() {" +
                       "    System.out.println(Collections.<String>emptyList());" +
                       "  }" +
                       "}";
    final String what3 = "'_q.'_method:[regex( println )]('_a)";
    final String by3 = "$q$.$method$($a$)";
    final String expected4 = "import java.util.Collections;\n" +
                             "\n" +
                             "import static java.lang.System.out;\n" +
                             "\n" +
                             "class X {  void m() {    out.println(Collections.<String>emptyList());  }}";
    assertEquals("don't break references with type parameters", expected4,
                 replace(in3, what3, by3, true, true));

    final String in4 = "import java.util.Collections;\n" +
                       "public class X {\n" +
                       "    void some() {\n" +
                       "        System.out.println(1);\n" +
                       "        boolean b = Collections.eq(null, null);\n" +
                       "    }\n" +
                       "}";
    final String what4 = "System.out.println(1);";
    final String by4 = "System.out.println(2);";
    final String expected5 = "import java.util.Collections;\n" +
                             "\n" +
                             "import static java.lang.System.out;\n" +
                             "\n" +
                             "public class X {\n" +
                             "    void some() {\n" +
                             "        out.println(2);\n" +
                             "        boolean b = Collections.eq(null, null);\n" +
                             "    }\n" +
                             "}";
    assertEquals("don't add static import to inaccessible members", expected5,
                 replace(in4, what4, by4, true, true));

    final String in5 = "package cz.ahoj.sample.annotations;\n" +
                       "/**\n" +
                       " * @author Ales Holy\n" +
                       " * @since 18. 7. 2017.\n" +
                       " */\n" +
                       "@OuterAnnotation({\n" +
                       "        @InnerAnnotation(classes = {Integer.class}),\n" +
                       "        @InnerAnnotation(classes = {String.class}),\n" +
                       "        @InnerAnnotation(classes = {ReplacementTest.ReplacementTestConfig.class})\n" +
                       "})\n" +
                       "public class ReplacementTest {\n" +
                       "    static class ReplacementTestConfig {\n" +
                       "    }\n" +
                       "}\n" +
                       "@interface InnerAnnotation {\n" +
                       "    Class<?>[] classes() default {};\n" +
                       "}\n" +
                       "@interface OuterAnnotation {\n" +
                       "\n" +
                       "    InnerAnnotation[] value();\n" +
                       "}";
    final String what5 = "@'_a:[regex( InnerAnnotation )](classes = { String.class })";
    final String by5 = "@$a$(classes = { Integer.class })\n" +
                       "@$a$(classes = { String.class })";
    assertEquals("add import when reference is just outside the class",

                 "package cz.ahoj.sample.annotations;\n" +
                 "\n" +
                 "import static cz.ahoj.sample.annotations.ReplacementTest.ReplacementTestConfig;\n" +
                 "\n" +
                 "/**\n" +
                 " * @author Ales Holy\n" +
                 " * @since 18. 7. 2017.\n" +
                 " */\n" +
                 "@OuterAnnotation({\n" +
                 "        @InnerAnnotation(classes = {Integer.class}),\n" +
                 "        @InnerAnnotation(classes = { Integer.class }),\n" +
                 "@InnerAnnotation(classes = { String.class }),\n" +
                 "        @InnerAnnotation(classes = {ReplacementTestConfig.class})\n" +
                 "})\n" +
                 "public class ReplacementTest {\n" +
                 "    static class ReplacementTestConfig {\n" +
                 "    }\n" +
                 "}\n" +
                 "@interface InnerAnnotation {\n" +
                 "    Class<?>[] classes() default {};\n" +
                 "}\n" +
                 "@interface OuterAnnotation {\n" +
                 "\n" +
                 "    InnerAnnotation[] value();\n" +
                 "}",
                 replace(in5, what5, by5, true, true));

    final String in6 = "class X {{" +
                       "  Predicate<String> p = Integer::valueOf;" +
                       "}}" +
                       "interface Predicate<T> {" +
                       "  boolean test(T t);" +
                       "}";
    final String what6 = "Integer::valueOf";
    final String by6 = "Boolean::valueOf";
    assertEquals("class X {{" +
                 "  Predicate<String> p = Boolean::valueOf;" +
                 "}}" +
                 "interface Predicate<T> {" +
                 "  boolean test(T t);" +
                 "}",
                 replace(in6, what6, by6, true));
  }

  public void testUseStaticStarImport() {
    final String in = "class ImportTest {{\n" +
                      "    Math.abs(-0.5);\n" +
                      "    Math.sin(0.5);\n" +
                      "    Math.max(1, 2);\n" +
                      "}}";
    final String what = "Math.'m('_a*)";
    final String by = "Math.$m$($a$)";
    final boolean save = options.isToUseStaticImport();
    options.setToUseStaticImport(true);
    try {

      // depends on default setting being equal to 3 for names count to use import on demand
      final String expected = "import static java.lang.Math.*;\n" +
                              "\n" +
                              "class ImportTest {{\n" +
                              "    abs(-0.5);\n" +
                              "    sin(0.5);\n" +
                              "    max(1, 2);\n" +
                              "}}";
      assertEquals("Replacing with static star import", expected, replace(in, what, by, true, true));
    } finally {
      options.setToUseStaticImport(save);
    }
  }

  public void testReformatAndShortenClassRefPerformance() throws IOException {
    options.setToReformatAccordingToStyle(true);

    final String source = loadFile("ReformatAndShortenClassRefPerformance_source.java");
    final String pattern = loadFile("ReformatAndShortenClassRefPerformance_pattern.java");
    final String replacement = loadFile("ReformatAndShortenClassRefPerformance_replacement.java");

    PlatformTestUtil.startPerformanceTest("SSR", 20000,
                                          () -> assertEquals("Reformat Performance", loadFile("ReformatPerformance_result.java"),
                                                             replace(source, pattern, replacement, true, true))).assertTiming();

    options.setToReformatAccordingToStyle(false);
    options.setToShortenFQN(true);

    PlatformTestUtil.startPerformanceTest("SSR", 20000,
                                          () -> assertEquals("Shorten Class Ref Performance", loadFile("ShortenPerformance_result.java"),
                                                             replace(source, pattern, replacement, true, true))).assertTiming();

  }

  public void testLeastSurprise() {
    String s1 = "@Nullable (a=String.class) @String class Test {\n" +
                "  void aaa(String t) {\n" +
                "    String a = String.valueOf(' ');" +
                "    String2 a2 = String2.valueOf(' ');" +
                "  }\n" +
                "}";
    String s2 = "'String:String";
    String s2_2 = "String";
    String s2_3 = "'String:java\\.lang\\.String";
    String s2_4 = "java.lang.String";
    String replacement = CommonClassNames.JAVA_UTIL_LIST;
    String expected = "@Nullable (a=java.util.List.class) @java.util.List class Test {\n" +
                "  void aaa(java.util.List t) {\n" +
                "    java.util.List a = java.util.List.valueOf(' ');" +
                "    String2 a2 = String2.valueOf(' ');" +
                "  }\n" +
                "}";

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
    String s1 = "try {\n" +
                "            em.persist(p);\n" +
                "        } catch (PersistenceException e) {\n" +
                "            // good\n" +
                "        }";
    String s2 = "try { '_TryStatement; } catch('_ExceptionType '_ExceptionDcl) { /* '_CommentContent */ }";
    String replacement = "try { $TryStatement$; } catch($ExceptionType$ $ExceptionDcl$) { _logger.warning(\"$CommentContent$\", $ExceptionDcl$); }";
    String expected = "try { em.persist(p); } catch(PersistenceException e) { _logger.warning(\" good\", e); }";

    assertEquals(expected, replace(s1, s2, replacement));

    final String in1 = "try {\n" +
                       "  System.out.println(1);\n" +
                       "} catch (RuntimeException e) {\n" +
                       "  System.out.println(2);\n" +
                       "} finally {\n" +
                       "  System.out.println(3);\n" +
                       "}\n";
    final String what1 = "try {\n" +
                         "  '_Statement1;\n" +
                         "} finally {\n" +
                         "  '_Statement2;\n" +
                         "}";
    final String by1 = "try {\n" +
                       "  // comment1\n" +
                       "  $Statement1$;\n" +
                       "} finally {\n" +
                       "  // comment2\n" +
                       "  $Statement2$;\n" +
                       "}";
    final String expected1 = "try {\n" +
                             "  // comment1\n" +
                             "  System.out.println(1);\n" +
                             "} catch (RuntimeException e) {\n" +
                             "  System.out.println(2);\n" +
                             "} finally {\n" +
                             "  // comment2\n" +
                             "  System.out.println(3);\n" +
                             "}\n";
    assertEquals("Replacing try/finally should leave unmatched catch sections alone",
                 expected1, replace(in1, what1, by1));

    final String in2 = "try (AutoCloseable a = null) {" +
                       "  System.out.println(1);" +
                       "} catch (Exception e) {" +
                       "  System.out.println(2);" +
                       "} finally {" +
                       "  System.out.println(3);" +
                       "}";
    final String what2 = "try {" +
                         "  '_Statement*;" +
                         "}";
    final String by2 = "try {" +
                       "  /* comment */" +
                       "  $Statement$;" +
                       "}";
    final String expected2 = "try (AutoCloseable a = null) {" +
                             "  /* comment */  System.out.println(1);" +
                             "} catch (Exception e) {" +
                             "  System.out.println(2);" +
                             "} finally {" +
                             "  System.out.println(3);" +
                             "}";
    assertEquals("Replacing try/finally should also keep unmatched resource lists and finally blocks",
                 expected2,
                 replace(in2, what2, by2));

    final String in3 = "class Foo {\n" +
                       "  {\n" +
                       "    try {\n" +
                       "    } catch (NullPointerException e) {\n" +
                       "    } catch (IllegalArgumentException e) {\n" +
                       "    } catch (Exception ignored) {\n" +
                       "    }\n" +
                       "  }\n" +
                       "}";
    final String what3 = "try {\n" +
                         "} catch(Exception ignored) {\n" +
                         "}";
    final String by3 = "try {\n" +
                       "  // 1\n" +
                       "} catch(Exception ignored) {\n" +
                       "  //2\n" +
                       "}";
    assertEquals("don't break the order of catch blocks",
                 "class Foo {\n" +
                 "  {\n" +
                 "    try {\n" +
                 "  // 1\n" +
                 "} catch (NullPointerException e) {\n" +
                 "    } catch (IllegalArgumentException e) {\n" +
                 "    } catch(Exception ignored) {\n" +
                 "  //2\n" +
                 "}\n" +
                 "  }\n" +
                 "}",
                 replace(in3, what3, by3));
  }

  public void testReplaceExtraSemicolon() {
    String in = "try {\n" +
                "      String[] a = {\"a\"};\n" +
                "      System.out.println(\"blah\");\n" +
                "} finally {\n" +
                "}\n";
    String what = "try {\n" + " '_statement*;\n" + "} finally {\n" + "  \n" + "}";
    String replacement = "$statement$;";
    String expected = "String[] a = {\"a\"};\n" +
                "      System.out.println(\"blah\");\n";

    assertEquals(expected, replace(in, what, replacement));

    String in2 = "try {\n" +
                  "    if (args == null) return ;\n" +
                  "    while(true) return ;\n" +
                  "    System.out.println(\"blah2\");\n" +
                  "} finally {\n" +
                  "}";
    String expected_2 = "if (args == null) return ;\n" +
                  "    while(true) return ;\n" +
                  "    System.out.println(\"blah2\");";

    assertEquals(expected_2, replace(in2, what, replacement));

    String in3 = "{\n" +
                  "    try {\n" +
                  "        System.out.println(\"blah1\");\n" +
                  "\n" +
                  "        System.out.println(\"blah2\");\n" +
                  "    } finally {\n" +
                  "    }\n" +
                  "}";
    String expected_3 = "{\n" +
                  "    System.out.println(\"blah1\");\n" +
                  "\n" +
                  "        System.out.println(\"blah2\");\n" +
                  "}";
    assertEquals(expected_3, replace(in3, what, replacement));

    String in4 = "{\n" +
                  "    try {\n" +
                  "        System.out.println(\"blah1\");\n" +
                  "        // indented comment\n" +
                  "        System.out.println(\"blah2\");\n" +
                  "    } finally {\n" +
                  "    }\n" +
                  "}";
    String expected_4 = "{\n" +
                  "    System.out.println(\"blah1\");\n" +
                  "        // indented comment\n" +
                  "        System.out.println(\"blah2\");\n" +
                  "}";
    assertEquals(expected_4, replace(in4, what, replacement));

    String in5 = "class X {\n" +
                 "    public void visitDocTag(String tag) {\n" +
                 "        String psiDocTagValue = null;\n" +
                 "        boolean isTypedValue = false;\n" +
                 "        {}\n" +
                 "    }\n" +
                 "}";
    String what5 = "void '_m('_T '_p) {\n" +
                   "  '_st*;\n" +
                   "}";
    String replacement5 = "    void $m$($T$ $p$) {\n" +
                          "        System.out.println();\n" +
                          "        $st$;\n" +
                          "    }";
    String expected5 = "class X {\n" +
                       "    public void visitDocTag(String tag) {\n" +
                       "        System.out.println();\n" +
                       "        String psiDocTagValue = null;\n" +
                       "        boolean isTypedValue = false;\n" +
                       "        {}\n" +
                       "    }\n" +
                       "}";
    assertEquals(expected5, replace(in5, what5, replacement5));
  }

  public void testReplaceFinalModifier() {
    String s1 = "class Foo {\n" +
                "  void foo(final int i,final int i2, final int i3) {\n" +
                "     final int x = 5;\n" +
                "  }\n" +
                "}";
    String s2 = "final '_type 'var = '_init?;";
    String s3 = "$type$ $var$ = $init$;";

    String expected = "class Foo {\n" +
                      "  void foo(int i, int i2, int i3) {\n" +
                      "     int x = 5;\n" +
                      "  }\n" +
                      "}";

    assertEquals(expected, replace(s1, s2, s3));
  }

  public void testKeepUnmatchedModifiers() {
    final String in = "class X {" +
                      "  private static final int foo = 1;" +
                      "}";
    final String expected = "class X {" +
                            "  protected static final int foo = 1;" +
                            "}";

    assertEquals(expected, replace(in, "private '_Type '_field = '_init;", "protected $Type$ $field$ = $init$;"));
  }

  public void testRemovingRedundancy() {
    String s1 = "int a = 1;\n" +
                "a = 2;\n" +
                "int b = a;\n" +
                "b2 = 3;";
    String s2 = "int '_a = '_i;\n" +
                "'_st*;\n" +
                "'_a = '_c;";
    String s3 = "$st$;\n" +
                "$c$ = $i$;";

    String expected = "2 = 1;\n" +
                      "int b = a;\n" +
                      "b2 = 3;";

    assertEquals(expected, replace(s1, s2, s3));

    String s2_2 = "int '_a = '_i;\n" +
                  "'_st*;\n" +
                  "int '_c = '_a;";
    String s3_2 = "$st$;\n" +
                  "int $c$ = $i$;";
    String expected_2 = "a = 2;\n" +
                        "int b = 1;\n" +
                        "b2 = 3;";

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
    String source = "import java.io.Externalizable;\n" +
                    "import java.io.Serializable;\n" +
                    "abstract class MyClass implements Serializable, java.util.List, Externalizable {}";
    String search = "class 'TestCase implements java.util.List, '_others* {\n    '_MyClassContent\n}";
    String replace = "class $TestCase$ implements $others$ {\n    $MyClassContent$\n}";
    String expectedResult = "import java.io.Externalizable;\n" +
                            "import java.io.Serializable;\n" +
                            "abstract class MyClass implements Serializable, Externalizable {\n    \n}";

    assertEquals(expectedResult, replace(source, search, replace, true));
  }

  public void testReplaceFieldWithEndOfLineComment() {
    String source = "class MyClass {\n" +
                    "    private String b;// comment\n" +
                    "    public void foo() {\n" +
                    "    }\n" +
                    "}";
    String search = "class 'Class {\n    '_Content*\n}";
    String replace = "class $Class$ {\n" +
                     "    void x() {}\n" +
                     "    $Content$\n" +
                     "    void bar() {}\n" +
                     "}";
    String expectedResult = "class MyClass {\n" +
                            "    void x() {}\n" +
                            "    private String b;// comment\n" +
                            "    public void foo() {\n" +
                            "    }\n" +
                            "    void bar() {}\n" +
                            "}";

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

    final String expected1c = "@SuppressWarnings(\"ALL\") class B {}";
    assertEquals("Should replace unmatched annotation parameters",
                 expected1c, replace(in1, "@SuppressWarnings class A {}", "@SuppressWarnings class B {}"));

    final String expected1d = "@ SuppressWarnings(\"ALL\")\n" +
                             "public class A {}";
    assertEquals("Should replace unmatched annotation parameters when matching just annotation",
                 expected1d, replace(in1, "@SuppressWarnings", "@ SuppressWarnings"));


    final String in2 = "class X {" +
                 "  @SuppressWarnings(\"unused\") String s;" +
                 "}";
    final String expected2a = "class X {" +
                             "  @SuppressWarnings({\"unused\", \"other\"}) String s;" +
                             "}";
    assertEquals(expected2a, replace(in2, "@SuppressWarnings(\"unused\") String '_s;",
                                    "@SuppressWarnings({\"unused\", \"other\"}) String $s$;"));

    final String expected2b = "class X {" +
                             "  @SuppressWarnings(\"unused\") String s = \"undoubtedly\";" +
                             "}";
    assertEquals(expected2b, replace(in2, "@'_Anno('_v) String '_s;", "@$Anno$($v$) String $s$ = \"undoubtedly\";"));

    final String expected2c = "class X {" +
                             "  @SuppressWarnings(value=\"unused\") String s;" +
                             "}";
    assertEquals(expected2c, replace(in2, "@'_A('_v='_x)", "@$A$($v$=$x$)"));

    final String expected2d = "class X {" +
                              "  @SuppressWarnings({\"unused\", \"raw\"}) String s;" +
                              "}";
    assertEquals(expected2d, replace(in2, "@'_A('_x)", "@$A$({$x$, \"raw\"})"));

    final String expected2e = "class X {" +
                              "  @SuppressWarnings(value={1,2}, value=\"unused\") String s;" +
                              "}";
    assertEquals(expected2e, replace(in2, "@'_A('_n='_v)", "@$A$($n$={1,2}, $n$=$v$)"));


    final String in3 = "class X {\n" +
                       "  @Language(value=\"RegExp\",\n" +
                       "            prefix=\"xxx\") String pattern;\n" +
                       "}";
    final String expected3 = "class X {\n" +
                             "  @ A(value=\"RegExp\",\n" +
                             "            prefix=\"xxx\", suffix=\"\") String pattern;\n" +
                             "}";
    assertEquals(expected3, replace(in3, "@'_A('_v*='_x)", "@ A($v$=$x$, suffix=\"\")"));

    final String in4 = "class X {" +
                       "  @Anno(one=1, two=1) String s;" +
                       "}";
    final String expected4 = "class X {" +
                             "  @Anno(one=1, two=1, three=1) String s;" +
                             "}";
    assertEquals(expected4, replace(in4, "@'_A('_p*=1)", "@$A$($p$=1, three=1)"));

    final String expected4b = "class X {  @Anno(one=2, two=1) String s;}";
    assertEquals(expected4b, replace(in4, "@'_A('_p:one =1)", "@$A$($p$=2)"));

    final String in5 = "@RunWith(SpringJUnit4ClassRunner.class)\n" +
                       "@ContextConfiguration(classes = {\n" +
                       "        ThisShallBeTwoClassesInContextHierarchyConfig.class,\n" +
                       "        SomeTest.SomeTestConfig.class,\n" +
                       "        WhateverConfig.class\n" +
                       "})\n" +
                       "@Transactional\n" +
                       "public class SomeTest {}";
    final String expected5 = "@RunWith(SpringJUnit4ClassRunner.class)\n" +
                             "@ContextHierarchy(classes = {\n" +
                             "        @ContextConfiguration(classes = {ThisShallBeTwoClassesInContextHierarchyConfig.class,\n" +
                             "        SomeTest.SomeTestConfig.class,\n" +
                             "        WhateverConfig.class, Object.class})\n" +
                             "})\n" +
                             "@Transactional\n" +
                             "public class SomeTest {}";
    assertEquals(expected5, replace(in5, "@ContextConfiguration(classes = {'_X*})", "@ContextHierarchy(classes = {\n" +
                                                                                    "        @ContextConfiguration(classes = {$X$, Object.class})\n" +
                                                                                    "})"));

    final String in6 = "class X {\n" +
                       "  @WastingTime @Override\n" +
                       "  public @Constant @Sorrow String value() {\n" +
                       "    return null;\n" +
                       "  }\n" +
                       "}";
    final String expected6 = "class X {\n" +
                             "  @WastingTime @Override\n" +
                             "  private @Constant @Sorrow String value() {\n" +
                             "    return null;\n" +
                             "  }\n" +
                             "}";
    assertEquals(expected6, replace(in6, "'_ReturnType '_method('_ParameterType '_parameter*);",
                                    "private $ReturnType$ $method$($ParameterType$ $parameter$);"));

    final String in7 = "public class IssueLink {\n" +
                       "    @XmlAttribute(name = \"default\", namespace = \"space\")\n" +
                       "    @Deprecated\n" +
                       "    public String typeInward;\n" +
                       "}";
    final String expected7 = "public class IssueLink {\n" +
                             "    @XmlAttribute(name=\"default\", namespace = \"space\")\n" +
                             "    public String typeInward;\n" +
                             "}";
    assertEquals(expected7, replace(in7, "@XmlAttribute(name=\"default\") @Deprecated '_Type '_field;",
                                    "@XmlAttribute(name=\"default\") $Type$ $field$;"));

    final String expected7b = "class IssueLink {\n" +
                              "    @XmlAttribute(name = \"default\", namespace = \"space\")\n" +
                              "    @Deprecated\n" +
                              "    public String typeInward;\n" +
                              "}";
    assertEquals(expected7b, replace(in7, "@'_Anno* public class '_X {}", "@$Anno$ class $X$ {}"));
  }

  public void testReplacePolyadicExpression() {
    final String in1 = "class A {" +
                      "  int i = 1 + 2 + 3;" +
                      "}";
    final String what1 = "1 + '_a+";

    final String by1 = "4";
    assertEquals("class A {  int i = 4;}", replace(in1, what1, by1));

    final String by2 = "$a$";
    assertEquals("class A {  int i = 2 + 3;}", replace(in1, what1, by2));

    final String by3 = "$a$+4";
    assertEquals("class A {  int i = 2 + 3+4;}", replace(in1, what1, by3));

    final String what2 = "1 + 2 + 3 + '_a*";
    final String by4 = "1 + 3 + $a$";
    assertEquals("class A {  int i = 1 + 3;}", replace(in1, what2, by4));

    final String by5 = "$a$ + 1 + 3";
    assertEquals("class A {  int i = 1 + 3;}", replace(in1, what2, by5));

    final String by6 = "1 + $a$ + 3";
    assertEquals("class A {  int i = 1 + 3;}", replace(in1, what2, by6));

    final String in2 = "class A {" +
                       "  boolean b = true && true;" +
                       "}";
    final String what3 = "true && true && '_a*";
    final String by7 = "true && true && $a$";
    assertEquals("class A {  boolean b = true && true;}", replace(in2, what3, by7));

    final String by8 = "$a$ && true && true";
    assertEquals("class A {  boolean b = true && true;}", replace(in2, what3, by8));

  }

  public void testReplaceAssert() {
    final String in = "class A {" +
                      "  void m(int i) {" +
                      "    assert 10 > i;" +
                      "  }" +
                      "}";

    final String what = "assert '_a > '_b : '_c?;";
    final String by = "assert $b$ < $a$ : $c$;";
    assertEquals("class A {  void m(int i) {    assert i < 10;  }}", replace(in, what, by));
  }

  public void testReplaceMultipleVariablesInOneDeclaration() {
    final String in = "class A {\n" +
                      "  private int i, /*1*/j, k;\n" +
                      "  void m() {\n" +
                      "    int i,\n" +
                      "        j,// 2\n" +
                      "        k;\n" +
                      "  }\n" +
                      "}\n";
    final String what1 = "int '_i+;";
    final String by1 = "float $i$;";
    assertEquals("class A {\n" +
                 "  private float i, /*1*/j, k;\n" +
                 "  void m() {\n" +
                 "    float i,\n" +
                 "        j,// 2\n" +
                 "        k;\n" +
                 "  }\n" +
                 "}\n",
                 replace(in, what1, by1));

    final String what2 = "int '_a, '_b, '_c = '_d?;";
    final String by2 = "float $a$, $b$, $c$ = $d$;";
    assertEquals("class A {\n" +
                 "  private float i, j, k;\n" +
                 "  void m() {\n" +
                 "    float i, j, k;\n" +
                 "  }\n" +
                 "}\n",
                 replace(in, what2, by2));
  }

  public void testReplaceWithScriptedVariable() {
    final String in = "class A {\n" +
                      "  void method(Object... os) {}\n" +
                      "  void f(Object a, Object b, Object c) {\n" +
                      "    method(a, b, c, \"one\" + \"two\");\n" +
                      "    method(a);\n" +
                      "  }\n" +
                      "}";
    final String what = "method('_arg+)";
    final String by = "method($newarg$)";
    final ReplacementVariableDefinition variable = options.addNewVariableDefinition("newarg");
    variable.setScriptCodeConstraint("arg.collect { \"(String)\" + it.getText() }.join(',')");

    final String expected = "class A {\n" +
                            "  void method(Object... os) {}\n" +
                            "  void f(Object a, Object b, Object c) {\n" +
                            "    method((String)a,(String)b,(String)c,(String)\"one\" + \"two\");\n" +
                            "    method((String)a);\n" +
                            "  }\n" +
                            "}";
    assertEquals(expected, replace(in, what, by));
    options.clearVariableDefinitions();

    final String in2 = "class Limitless {\n" +
                 "    public int id;\n" +
                 "    public String field;\n" +
                 "    public Limitless() {\n" +
                 "        this.field = \"default\";\n" +
                 "        this.id = 01;\n" +
                 "    }\n" +
                 "    public int getId() {\n" +
                 "        return id;\n" +
                 "    }\n" +
                 "    public String getField() { return field; }\n" +
                 "    public static void main(String [] args) {\n" +
                 "        Limitless myClass = new Limitless();\n" +
                 "        System.out.println(myClass.getField()+\" \"+myClass.getId());\n" +
                 "        Example example = new Example(1, \"name\");\n" +
                 "        int r = example.getI()+9;\n" +
                 "        myClass.getId();\n" +
                 "    }\n" +
                 "}";
    final String what2 = "'_Instance:[exprtype( Limitless )].'property:[regex( get(.*) )]()";
    final String by2 = "$Instance$.$field$";
    final ReplacementVariableDefinition variable2 = options.addNewVariableDefinition("field");
    variable2.setScriptCodeConstraint("String name = property.methodExpression.referenceName[3..-1]\n" +
                                      "name[0].toLowerCase() + name[1..-1]");
    assertEquals("class Limitless {\n" +
                 "    public int id;\n" +
                 "    public String field;\n" +
                 "    public Limitless() {\n" +
                 "        this.field = \"default\";\n" +
                 "        this.id = 01;\n" +
                 "    }\n" +
                 "    public int getId() {\n" +
                 "        return id;\n" +
                 "    }\n" +
                 "    public String getField() { return field; }\n" +
                 "    public static void main(String [] args) {\n" +
                 "        Limitless myClass = new Limitless();\n" +
                 "        System.out.println(myClass.field+\" \"+myClass.id);\n" +
                 "        Example example = new Example(1, \"name\");\n" +
                 "        int r = example.getI()+9;\n" +
                 "        myClass.id;\n" +
                 "    }\n" +
                 "}", replace(in2, what2, by2));
    options.clearVariableDefinitions();
  }

  public void testMethodContentReplacement() {
    final String in = "class A extends TestCase {\n" +
                      "  void testOne() {\n" +
                      "    System.out.println();\n" +
                      "  }\n" +
                      "}\n";
    final String what = "class '_A { void '_b:[regex( test.* )](); }";
    final String by = "class $A$ {\n  @java.lang.Override void $b$();\n}";
    assertEquals("class A extends TestCase {\n" +
                 "  @Override void testOne() {\n" +
                 "    System.out.println();\n" +
                 "  }\n" +
                 "}\n", replace(in, what, by, true));

    final String what2 = "void '_a:[regex( test.* )]();";
    final String by2 = "@org.junit.Test void $a$();";
    assertEquals("class A extends TestCase {\n" +
                 "  @org.junit.Test void testOne() {\n" +
                 "    System.out.println();\n" +
                 "  }\n" +
                 "}\n",
                 replace(in, what2, by2));
  }

  public void testReplaceMethodWithoutBody() {
    final String in = "abstract class A {\n" +
                      "  abstract void a();\n" +
                      "}";
    final String what = "void '_a();";
    final String by = "void $a$(int i);";
    assertEquals("abstract class A {\n" +
                 "  abstract void a(int i);\n" +
                 "}",
                 replace(in, what, by));

    final String what2 = "abstract void '_a('_T '_p*);";
    final String by2 = "void $a$($T$ $p$) {}";
    assertEquals("abstract class A {\n" +
                 "  void a() {}\n" +
                 "}",
                 replace(in, what2, by2));
  }

  public void testReplaceParameterWithComment() {
    final String in = "class A {\n" +
                      "  void a(int b) {}\n" +
                      "}";
    final String what = "int '_a = '_b{0,1};";
    final String by = "final long /*!*/ $a$ = $b$;";
    assertEquals("class A {\n" +
                 "  void a(final long /*!*/ b) {}\n" +
                 "}",
                 replace(in, what, by));

    final String in2 = "class X {" +
                       "  void m() {" +
                       "    for (int x : new int[]{1, 2, 3}) {}" +
                       "  }" +
                       "}";
    final String what2 = "'_T '_v = '_i{0,1};";
    final String by2 = "final $T$ /*!*/ $v$ = $i$;";
    assertEquals("foreach parameter replaced incorrectly",
                 "class X {" +
                 "  void m() {" +
                 "    for (final int /*!*/ x : new int[]{1, 2, 3}) {}" +
                 "  }" +
                 "}",
                 replace(in2, what2, by2));
  }

  public void testReplaceInnerClass() {
    String in = "public class A {\n" +
                 "  public class B<T> extends A implements java.io.Serializable {}\n" +
                 "}";
    String what = "class '_A {" +
                   "  class '_B {}" +
                   "}";
    String by = "class $A$ {\n" +
                 "  private class $B$ {\n" +
                 "  }\n" +
                 "}";
    assertEquals("public class A {\n" +
                 "  private class B<T> extends A implements java.io.Serializable {\n" +
                 "  }\n" +
                 "}",
                 replace(in, what, by));

    String in2 = "public class A {\n" +
                 "  void m1() {}\n" +
                 "  public void m2() {}\n" +
                 "  public class B<T> extends A implements java.io.Serializable {\n" +
                 "    int zero() {\n" +
                 "      return 0;\n" +
                 "    }\n" +
                 "  }\n" +
                 "  void m3() {}\n" +
                 "}";
    assertEquals("should replace unmatched class content correctly",
                 "public class A {\n" +
                 "  void m1() {}\n" +
                 "  public void m2() {}\n" +
                 "  private class B<T> extends A implements java.io.Serializable {\n" +
                 "    int zero() {\n" +
                 "      return 0;\n" +
                 "    }\n" +
                 "  }\n" +
                 "  void m3() {}\n" +
                 "}",
                 replace(in2, what, by));
  }

  public void testReplaceQualifiedReference() {
    String in = "class A {" +
                "  String s;" +
                "  void setS(String s) {" +
                "    System.out.println(this.s);" +
                "    this.s = s;" +
                "  }" +
                "}";
    String what = "System.out.println('_a);";
    String by = "System.out.println(\"$a$\" + $a$);";
    assertEquals("don't drop this",
                 "class A {" +
                 "  String s;" +
                 "  void setS(String s) {" +
                 "    System.out.println(\"this.s\" + this.s);" +
                 "    this.s = s;" +
                 "  }" +
                 "}",
                 replace(in, what, by));
  }

  public void testReplaceExpressionStatement() {
    String in = "class A {" +
                "  void m() {" +
                "    new Object();" +
                "  }" +
                "}";
    String what = "'_expr;";
    String by = "$expr$.toString();";
    assertEquals("too many semicolons",
                 "class A {" +
                 "  void m() {" +
                 "    new Object().toString();" +
                 "  }" +
                 "}",
                 replace(in, what, by, true));
  }

  public void testReplaceVariableInitializer() {
    String in = "class X {" +
                "  private final int i = 1;" +
                "}";
    String what = "int '_v;";
    String by = "long $v$;";
    assertEquals("initializer should remain",
                 "class X {" +
                 "  private final long i=1;" +
                 "}",
                 replace(in, what, by, true));
  }

  public void testReplaceParentheses() {
    String in = "public class MyFile {\n" +
                "    void test(String a, Object b) {\n" +
                "        if(a.length() == 0) {\n" +
                "            System.out.println(\"empty\");\n" +
                "        }\n" +
                "        if(((String) b).length() == 0) {\n" +
                "            System.out.println(\"empty\");\n" +
                "        }\n" +
                "    }\n" +
                "}";

    String what = "'_expr:[exprtype( String )].length() == 0";
    String by = "$expr$.isEmpty()";
    assertEquals("parentheses should remain",

                 "public class MyFile {\n" +
                 "    void test(String a, Object b) {\n" +
                 "        if(a.isEmpty()) {\n" +
                 "            System.out.println(\"empty\");\n" +
                 "        }\n" +
                 "        if(((String) b).isEmpty()) {\n" +
                 "            System.out.println(\"empty\");\n" +
                 "        }\n" +
                 "    }\n" +
                 "}",
                 replace(in, what, by, true));

    options.getMatchOptions().setRecursiveSearch(true);
    String in2 = "class X {{" +
                 "  int i = (((3)));" +
                 "}}";
    String what2 = "('_expr:[exprtype( int )])";
    String by2 = "2";
    assertEquals("don't throw exceptions when replacing",
                 "class X {{" +
                 "  int i = 2;" +
                 "}}",
                 replace(in2, what2, by2, true));
  }

  public void testReplaceTarget() {
    String in = "import org.junit.Test;" +
                "class Help {" +
                "  private String s = \"hello\";" +
                "  @Test" +
                "  public void testThisThing(){" +
                "    System.out.println();" +
                "    System.out.println();" +
                "    System.out.println();" +
                "    s = null;" +
                "  }" +
                "}";
    String what = "class 'Class {" +
                  "  '_FieldType '_FieldName;" +
                  "  @'_Annotation" +
                  "  '_MethodType '_MethodName() {" +
                  "    '_Statement*;" +
                  "    '_FieldName = null;" +
                  "  }" +
                  "}";
    String by = "class $Class$ {" +
                "  $FieldType$ $FieldName$;" +
                "  @$Annotation$" +
                "  $MethodType$ $MethodName$() {" +
                "    $Statement$;" +
                "  }" +
                "}";
    assertEquals("import org.junit.Test;" +
                 "class Help {" +
                 "  private String s=\"hello\";" +
                 "  @Test" +
                 "  public  void testThisThing() {" +
                 "    System.out.println();" +
                 "    System.out.println();" +
                 "    System.out.println();" +
                 "  }" +
                 "}", replace(in, what, by, true));
  }

  public void testReplaceGenerics() {
    options.setToShortenFQN(false);
    String in = "import java.util.ArrayList;" +
                "import java.util.List;" +
                "class X {" +
                "  List<String> list = new java.util.LinkedList<String>();" +
                "  List<Integer> list2 = new java.util.ArrayList<Integer>();" +
                "  List<Double> list3 = new ArrayList<>();" +
                "}";

    assertEquals("should properly replace with diamond",
                 "import java.util.ArrayList;" +
                 "import java.util.List;" +
                 "class X {" +
                 "  List<String> list = new java.util.LinkedList<>();" +
                 "  List<Integer> list2 = new ArrayList<>();" +
                 "  List<Double> list3 = new ArrayList<>();" +
                 "}",
                 replace(in, "new '_X<'_p+>()", "new $X$<>()", true));
    assertEquals("should keep generics when matching without",
                 "import java.util.ArrayList;" +
                 "import java.util.List;" +
                 "class X {" +
                 "  List<String> list = new /*1*/java.util.LinkedList<String>();" +
                 "  List<Integer> list2 = new /*1*/ArrayList<Integer>();" +
                 "  List<Double> list3 = new /*1*/ArrayList<>();" +
                 "}",
                 replace(in, "new '_X()", "new /*1*/$X$()", true));
    assertEquals("should not duplicate generic parameters",
                 "import java.util.ArrayList;" +
                 "import java.util.List;" +
                 "class X {" +
                 "  List<String> list = new java.util.LinkedList</*0*/String>();" +
                 "  List<Integer> list2 = new ArrayList</*0*/Integer>();" +
                 "  List<Double> list3 = new ArrayList<>();" +
                 "}",
                 replace(in, "new '_X<'_p+>()", "new $X$</*0*/$p$>()", true));
  }

  public void testArrays() {
    String in = "public abstract class Bar {\n" +
                "    String[] x;\n" +
                "    abstract String[] foo(String[] x);\n" +
                "}";

    assertEquals("should keep array brackets 1",
                 "public abstract class Bar {\n" +
                 "    String[] x;\n" +
                 "    abstract String[] foo(String[] x);\n" +
                 "}",
                 replace(in, "'_FieldType 'Field = '_Init?;", "$FieldType$ $Field$ = $Init$;", true));

    assertEquals("should keep array brackets 2",
                 "public abstract class Bar {\n" +
                 "    String[] x;\n" +
                 "    abstract String[] foo (String[] x);\n" +
                 "}",
                 replace(in, "'_ReturnType '_Method('_ParameterType '_Parameter*);",
                         "$ReturnType$ $Method$ ($ParameterType$ $Parameter$);", true));

    String in2 = "class X {" +
                "  public final X[] EMPTY_ARRAY = {};" +
                "}";
    assertEquals("shouldn't delete semicolon",
                 "class X {" +
                 "  public final X[] EMPTY_ARRAY = {};" +
                 "}",
                 replace(in2, "'_FieldType 'Field = '_Init?;", "$FieldType$ $Field$ = $Init$;", true));
  }

  public void testMethodCall() {
    String in = "class X {" +
                "  void x() {}" +
                "  void y() {" +
                "    x();" +
                "    this.x();" +
                "  }" +
                "}";
    assertEquals("replace (un)qualified calls correctly",
                 "class X {" +
                 "  void x() {}" +
                 "  void y() {" +
                 "    x();" +
                 "    this.x();" +
                 "  }" +
                 "}",
                 replace(in, "'_Instance?.'_MethodCall('_arguments*)", "$Instance$.$MethodCall$($arguments$)", true));
  }

  public void testKeepModifierFormatting() {
    String in = "@Deprecated\n" +
                "public class X {}";
    final String what = "class '_X {}";
    final String replacement = "/** comment */\n" +
                               "class $X$ {}";
    final String expected = "/** comment */\n" +
                            "@Deprecated\n" +
                            "public class X {}";
    assertEquals("keep newline in modifier list",
                 expected, replace(in, what, replacement, true));
  }
}