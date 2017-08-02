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
import com.intellij.psi.CommonClassNames;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Maxim.Mossienko
 */
@SuppressWarnings({"ALL"})
public class StructuralReplaceTest extends StructuralReplaceTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final MatchOptions matchOptions = this.options.getMatchOptions();
    matchOptions.setFileType(StdFileTypes.JAVA);
    matchOptions.setLooseMatching(true);
  }

  public void testReplaceInLiterals() {
    String s1 = "String ID_SPEED = \"Speed\";";
    String s2 = "String 'name = \"'_string\";";
    String s2_2 = "String 'name = \"'_string:[regex( .* )]\";";
    String s3 = "VSegAttribute $name$ = new VSegAttribute(\"$string$\");";
    String expectedResult = "VSegAttribute ID_SPEED = new VSegAttribute(\"Speed\");";

    assertEquals(
      "Matching/replacing literals",
      expectedResult,
      replacer.testReplace(s1,s2,s3,options)
    );

    assertEquals(
      "Matching/replacing literals",
      expectedResult,
      replacer.testReplace(s1,s2_2,s3,options)
    );

    String s4 = "params.put(\"BACKGROUND\", \"#7B528D\");";
    String s5 = "params.put(\"$FieldName$\", \"#$exp$\");";
    String s6 = "String $FieldName$ = \"$FieldName$\";\n" +
                "params.put($FieldName$, \"$exp$\");";
    String expectedResult2 = "String BACKGROUND = \"BACKGROUND\";\n" +
                             "params.put(BACKGROUND, \"7B528D\");";

    assertEquals(
      "string literal replacement 2",
      expectedResult2,
      replacer.testReplace(s4,s5,s6,options)
    );

    String s7 = "IconLoader.getIcon(\"/ant/property.png\");\n" +
                "IconLoader.getIcon(\"/ant/another/property.png\");\n";
    String s8 = "IconLoader.getIcon(\"/'_module/'_name:[regex( \\w+ )].png\");";
    String s9 = "Icons.$module$.$name$;";
    String expectedResult3 = "Icons.ant.property;\n" +
                             "IconLoader.getIcon(\"/ant/another/property.png\");\n";

    assertEquals(
      "string literal replacement 3",
      expectedResult3,
      replacer.testReplace(s7,s8,s9,options)
    );

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

    assertEquals(
      "string literal replacement 4",
      expectedResult4,
      replacer.testReplace(s10,s11,s12,options)
    );
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
    String s2 = "JOptionPane.'showDialog(null, '_msg);";
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

    assertEquals(
      "adding comment to statement inside the if body",
      expectedResult,
      replacer.testReplace(s1,s2,s3,options)
    );

    String s4 = "myButton.setText(\"Ok\");";
    String s5 = "'_Instance.'_MethodCall:[regex( setText )]('_Parameter*:[regex( \"Ok\" )]);";
    String s6 = "$Instance$.$MethodCall$(\"OK\");";

    String expectedResult2 = "myButton.setText(\"OK\");";

    assertEquals(
      "adding comment to statement inside the if body",
      expectedResult2,
      replacer.testReplace(s4,s5,s6,options)
    );
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
                             "    lastTest = \"several constructions match\";\n" +
                             "    matches = testMatcher.findMatches(s5, s4, options);\n" +
                             "    if (matches == null || matches.size() != 3) return false;\n" +
                             "\n" +
                             "    // searching for several constructions\n" +
                             "    assertEquals(\"several constructions 2\", testMatcher.findMatches(s5, s6, options).size(), 0);\n" +
                             "\n" +
                             "    //options.setLooseMatching(true);\n" +
                             "    // searching for several constructions\n" +
                             "    assertEquals(\"several constructions 3\", testMatcher.findMatches(s7, s8, options).size(), 2);";

    String str4 = "";

    options.setToReformatAccordingToStyle(true);
    assertEquals("Basic replacement with formatter", expectedResult1, replacer.testReplace(str,str2,str3,options));
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
    assertEquals("Empty replacement", expectedResult2, replacer.testReplace(str,str2,str4,options));

    String str5 = "testMatcher.findMatches('_In,'_Pattern, options).size()";
    String str6 = "findMatchesCount($In$,$Pattern$)";
    String expectedResult3="// searching for several constructions\n" +
                           "    lastTest = \"several constructions match\";\n" +
                           "    matches = testMatcher.findMatches(s5, s4, options);\n" +
                           "    if (matches == null || matches.size() != 3) return false;\n" +
                           "\n" +
                           "    // searching for several constructions\n" +
                           "    assertEquals(\"several constructions 2\", findMatchesCount(s5,s6), 0);\n" +
                           "\n" +
                           "    //options.setLooseMatching(true);\n" +
                           "    // searching for several constructions\n" +
                           "    assertEquals(\"several constructions 3\", findMatchesCount(s7,s8), 2);";
    assertEquals("Expression replacement", expectedResult3, replacer.testReplace(expectedResult1,str5,str6,options));

    String str7 = "try { a.doSomething(); b.doSomething(); } catch(IOException ex) {  ex.printStackTrace(); throw new RuntimeException(ex); }";
    String str8 = "try { 'Statements+; } catch('_ '_) { 'HandlerStatements+; }";
    String str9 = "$Statements$;";
    String expectedResult4 = "a.doSomething(); b.doSomething();";

    assertEquals("Multi line match in replacement", expectedResult4, replacer.testReplace(str7,str8,str9,options));

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

    assertEquals("Array initializer replacement", expectedResult5, replacer.testReplace(str10,str11,str12,options));

    String str13 = "  aaa(5,6,3,4,1,2);";
    String str14 = "aaa('_t{2,2},3,4,'_q{2,2});";
    String str15 = "aaa($q$,3,4,$t$);";
    String expectedResult6 = "  aaa(1,2,3,4,5,6);";

    assertEquals("Parameter multiple match", expectedResult6, replacer.testReplace(str13,str14,str15,options));

    String str16 = "  int c = a();";
    String str17 = "'_t:a ('_q*,'_p*)";
    String str18 = "$t$($q$,1,$p$)";
    String expectedResult7 = "  int c = a(1);";

    assertEquals("Replacement of init in definition + empty substitution", expectedResult7, replacer.testReplace(str16,str17,str18,options));

    String str19 = "  aaa(bbb);";
    String str20 = "'t('_);";
    String str21 = "$t$(ccc);";
    String expectedResult8 = "  aaa(ccc);";

    assertEquals("One substition replacement", expectedResult8, replacer.testReplace(str19,str20,str21,options));

    String str22 = "  instance.setAAA(anotherInstance.getBBB());";
    String str23 = "  '_i.'_m:set(.+) ('_a.'_m2:get(.+) ());";
    String str24 = "  $a$.set$m2_1$( $i$.get$m_1$() );";
    String expectedResult9 = "  anotherInstance.setBBB( instance.getAAA() );";

    assertEquals("Reg exp substitution replacement", expectedResult9, replacer.testReplace(str22,str23,str24,options));

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
    String str26 = "  LaterInvocator.invokeLater('Params{1,10});";
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

    assertEquals("Anonymous in parameter", expectedResult10, replacer.testReplace(str25,str26,str27,options));

    String str28 = "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile,\n" +
                   "                                                          processedElement.getTextOffset(), true,\n" +
                   "                                                          !myUsageViewDescriptor.toMarkInvalidOrReadonlyUsages(), null);";
    String str29 = "new UTElementNode('_param, '_directory, '_null, '_0, '_true, !'_descr.toMarkInvalidOrReadonlyUsages(),\n" +
                   "  'referencesWord)";
    String str30 = "new UTElementNode($param$, $directory$, $null$, $0$, $true$, true,\n" +
                   "  $referencesWord$)";

    String expectedResult11 = "UTElementNode elementNode = new UTElementNode(myProject, processedElement, psiFile, processedElement.getTextOffset(), true, true,\n" +
                              "  null);";
    assertEquals("Replace in def initializer", expectedResult11, replacer.testReplace(str28,str29,str30,options));

    String s31 = "a = b; b = c; a=a; c=c;";
    String s32 = "'a = 'a;";
    String s33 = "1 = 1;";
    String expectedResult12 = "a = b; b = c; 1 = 1; 1 = 1;";

    assertEquals("replace silly assignments", expectedResult12, replacer.testReplace(s31,s32,s33,options));

    String s34 = "ParamChecker.isTrue(1==1, \"!!!\");";
    String s35 = "ParamChecker.isTrue('_expr, '_msg);";
    String s36 = "assert $expr$ : $msg$;";

    String expectedResult13 = "assert 1==1 : \"!!!\";";
    assertEquals("replace with assert", expectedResult13, replacer.testReplace(s34,s35,s36,options));

    String s37 = "try { \n" +
                 "  ParamChecker.isTrue(1==1, \"!!!\");\n  \n" +
                 "  // comment we want to leave\n  \n" +
                 "  ParamChecker.isTrue(2==2, \"!!!\");\n" +
                 "} catch(Exception ex) {}";
    String s38 = "try {\n" +
                 "  'Statement{0,100};\n" +
                 "} catch(Exception ex) {}";
    String s39 = "$Statement$;";

    String expectedResult14 = "ParamChecker.isTrue(1==1, \"!!!\");\n  \n" +
                              "  // comment we want to leave\n  \n" +
                              "  ParamChecker.isTrue(2==2, \"!!!\");";
    assertEquals("remove try with comments inside", expectedResult14, replacer.testReplace(s37,s38,s39,options));

    String s40 = "ParamChecker.instanceOf(queryKey, GroupBySqlTypePolicy.GroupKey.class);";
    String s41 = "ParamChecker.instanceOf('_obj, '_class.class);";
    String s42 = "assert $obj$ instanceof $class$ : \"$obj$ is an instance of \" + $obj$.getClass() + \"; expected \" + $class$.class;";
    String expectedResult15 = "assert queryKey instanceof GroupBySqlTypePolicy.GroupKey : \"queryKey is an instance of \" + queryKey.getClass() + \"; expected \" + GroupBySqlTypePolicy.GroupKey.class;";

    assertEquals("Matching/replacing .class literals", expectedResult15, replacer.testReplace(s40,s41,s42,options));

    String s43 = "class Wpd {\n" +
                 "  static final String TAG_BEAN_VALUE = \"\";\n" +
                 "}\n" +
                 "XmlTag beanTag = rootTag.findSubTag(Wpd.TAG_BEAN_VALUE);";
    String s44 = "'_Instance?.findSubTag( '_Parameter:[exprtype( *String ) ])";
    String s45 = "jetbrains.fabrique.util.XmlApiUtil.findSubTag($Instance$, $Parameter$)";
    String expectedResult16 = "class Wpd {\n" +
                              "  static final String TAG_BEAN_VALUE = \"\";\n" +
                              "}\n" +
                              "XmlTag beanTag = jetbrains.fabrique.util.XmlApiUtil.findSubTag(rootTag, Wpd.TAG_BEAN_VALUE);";

    assertEquals("Matching/replacing static fields", expectedResult16, replacer.testReplace(s43,s44,s45,options));

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
    assertEquals("Replace in constructor", expectedResult17, replacer.testReplace(s46,s47,s48,options));

    String s49 = "class A {}\n" +
                 "class B extends A {}\n" +
                 "A a = new B();";
    String s50 = "A '_b = new '_B:*A ();";
    String s51 = "A $b$ = new $B$(\"$b$\");";
    String expectedResult18 = "class A {}\n" +
                              "class B extends A {}\n" +
                              "A a = new B(\"a\");";

    assertEquals("Class navigation", expectedResult18, replacer.testReplace(s49,s50,s51,options));

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
      assertEquals("Try/finally unwrapped with strict matching", expectedResult19, replacer.testReplace(s52, s53, s54, options));
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
    assertEquals("Try/finally unwrapped with loose matching", expectedResult19Loose, replacer.testReplace(s52, s53, s54, options));


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

    assertEquals("for with foreach", expectedResult20, replacer.testReplace(s55,s56,s57,options));

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

    assertEquals("replace symbol in definition", expectedResult21, replacer.testReplace(s58,s59,s60,options));

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

    assertEquals("Replacement of the comment with javadoc", expectedResult23, replacer.testReplace(s64,s65,s66,options));

    String s61 = "try { 1=1; } catch(Exception e) { 1=1; } catch(Throwable t) { 2=2; }";
    String s62 = "try { '_a; } catch(Exception e) { '_b; }";
    String s63 = "try { $a$; } catch(Exception1 e) { $b$; } catch(Exception2 e) { $b$; }";
    String expectedResult22 = "try { 1=1; } catch(Exception1 e) { 1=1; } catch(Exception2 e) { 1=1; } catch(Throwable t) { 2=2; }";

    assertEquals("try replacement by another try will leave the unmatched catch",
                 expectedResult22,
                 replacer.testReplace(s61,s62,s63,options));

  }

  public void testReplaceExpr() {
    String s1 = "new SimpleDateFormat(\"yyyyMMddHHmmss\")";
    String s2 = "'expr";
    String s3 = "new AtomicReference<DateFormat>($expr$)";
    String expectedResult = "new AtomicReference<DateFormat>(new SimpleDateFormat(\"yyyyMMddHHmmss\"))";

    assertEquals("Replacement of top-level expression only", expectedResult, replacer.testReplace(s1, s2, s3, options));

    String s4 = "get(\"smth\")";
    String s5 = "'expr";
    String s6 = "new Integer($expr$)";
    String expectedResult1 = "new Integer(get(\"smth\"))";

    assertEquals("Replacement of top-level expression only", expectedResult1, replacer.testReplace(s4, s5, s6, options));
  }

  public void testReplaceParameter() {
    String s1 = "class A { void b(int c, int d, int e) {} }";
    String s2 = "int d;";
    String s3 = "int d2;";
    String expectedResult = "class A { void b(int c, int d2, int e) {} }";

    assertEquals("replace method parameter", expectedResult, replacer.testReplace(s1,s2,s3,options));
  }

  public void testReplaceWithComments() {
    String s1 = "map.put(key, value); // line 1";
    String s2 = "map.put(key, value); // line 1";
    String s3 = "map.put(key, value); // line 1";
    String expectedResult = "map.put(key, value); // line 1";

    assertEquals("replace self with comment after", expectedResult, replacer.testReplace(s1,s2,s3,options));

    String s4 = "if (true) System.out.println(\"1111\"); else System.out.println(\"2222\");\n" +
                "while(true) System.out.println(\"1111\");";
    String s5 = "System.out.println('Test);";
    String s6 = "/* System.out.println($Test$); */";
    String expectedResult2 = "if (true) /* System.out.println(\"1111\"); */; else /* System.out.println(\"2222\"); */;\n" +
                             "while(true) /* System.out.println(\"1111\"); */;";
    assertEquals("replace with comment", expectedResult2, replacer.testReplace(s4,s5,s6,options));
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
    String expectedResult1 = "    {\n" +
                             "        System.out.println(3);\n" +
                             "        System.out.println(2);\n" +
                             "        System.out.println(1);\n" +
                             "    }\n" +
                             "    {\n" +
                             "        System.out.println(3);\n" +
                             "        System.out.println(2);\n" +
                             "        System.out.println(1);\n" +
                             "    }\n" +
                             "    {\n" +
                             "        System.out.println(3);\n" +
                             "        System.out.println(2);\n" +
                             "        System.out.println(1);\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    assertEquals("three statements replacement", expectedResult1, replacer.testReplace(s1,s2,s3,options));
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
    assertEquals("extra ;", expectedResult2, replacer.testReplace(s4,s5,s6,options));

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
    assertEquals("extra ; 2", expectedResult3, replacer.testReplace(s7,s8,s9,options));

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
                 "                'l{2,2};\n" +
                 "            }\n" +
                 "            public void run2() {\n" +
                 "                'l;\n" +
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

    assertEquals("same multiple occurences 2 times", expectedResult4, replacer.testReplace(s10,s11,s12,options));

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
                 "      'T{1,1000};\n" +
                 "    }\n" +
                 "    finally {\n" +
                 "      PsiLock.LOCK.release();\n" +
                 "    }";
    String s15 = "synchronized(PsiLock.LOCK) {\n" +
                 "  $T$;\n" +
                 "}";

    String expectedResult5 = "    synchronized (PsiLock.LOCK) {\n" +
                             "        return value;\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; over return", expectedResult5, replacer.testReplace(s13,s14,s15,options));
    options.setToReformatAccordingToStyle(false);


    String expectedResult6 = "    synchronized (PsiLock.LOCK) {\n" +
                             "        if (true) {\n" +
                             "            return value;\n" +
                             "        }\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    assertEquals("extra ; over if", expectedResult6, replacer.testReplace(s13_2,s14,s15,options));
    options.setToReformatAccordingToStyle(false);


    String expectedResult7 = "    synchronized (PsiLock.LOCK) {\n" +
                             "        if (true) {\n" +
                             "            return value;\n" +
                             "        }\n" +
                             "\n" +
                             "        if (true) {\n" +
                             "            return value;\n" +
                             "        }\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    assertEquals("newlines in matches of several lines", expectedResult7, replacer.testReplace(s13_3,s14,s15,options));
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
                 "  'Statement*;\n" +
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

    assertEquals("extra ;", expectedResult8, replacer.testReplace(s16,s17,s18,options));

    assertEquals("missed ;", expectedResult8_2, replacer.testReplace(s16_2,s17,s18,options));
  }

  public void testClassReplacement() {
    boolean formatAccordingToStyle = options.isToReformatAccordingToStyle();
    options.setToReformatAccordingToStyle(true);

    String s1 = "class A { public void b() {} }";
    String s2 = "class 'a { '_Other* }";
    String s3 = "class $a$New { Logger LOG; $Other$ }";
    String expectedResult = "    class ANew {\n" +
                            "        Logger LOG;\n\n" +
                            "        public void b() {\n" +
                            "        }\n" +
                            "    }";
    assertEquals(
      "Basic class replacement",
      expectedResult,
      replacer.testReplace(s1,s2,s3,options)
    );

    String s4 = "class A { class C {} public void b() {} int f; }";
    String s5 = "class 'a { '_Other* }";
    String s6 = "class $a$ { Logger LOG; $Other$ }";
    String expectedResult2 = "    class A {\n" +
                             "        Logger LOG;\n\n" +
                             "        class C {\n" +
                             "        }\n\n" +
                             "        public void b() {\n" +
                             "        }\n\n" +
                             "        int f;\n" +
                             "    }";

    assertEquals("Order of members in class replacement", expectedResult2, replacer.testReplace(s4,s5,s6,options));

    String s7 = "class A extends B { int c; void b() {} { a = 1; } }";
    String s8 = "class 'A extends B { '_Other* }";
    String s9 = "class $A$ extends B2 { $Other$ }";
    String expectedResult3 = "    class A extends B2 {\n" +
                             "        int c;\n\n" +
                             "        void b() {\n" +
                             "        }\n\n" +
                             "        {\n" +
                             "            a = 1;\n" +
                             "        }\n" +
                             "    }";

    assertEquals("Unsupported pattern exception", expectedResult3, replacer.testReplace(s7, s8, s9, options));
    options.setToReformatAccordingToStyle(formatAccordingToStyle);

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
    String expectedResult4 = "    /**\n" +
                             "     * @example\n" +
                             "     */\n" +
                             "    public class A {\n" +
                             "        class C {\n" +
                             "        }\n\n" +
                             "        public void b() {\n" +
                             "        }\n\n" +
                             "        int f;\n" +
                             "    }";

    options.setToReformatAccordingToStyle(true);
    assertEquals("Make class public", expectedResult4, replacer.testReplace(s10, s11, s12, options));
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
    String s14 = "class 'Class extends Thread {\n" +
                 "  'Class('_ParameterType* '_ParameterName*) {\n" +
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

    String expectedResult5 = "    class CustomThread extends CustomThread {\n" +
                             "        CustomThread(InputStream in, OutputStream out, boolean closeOutOnExit) {\n" +
                             "            super(\"CustomThread\");\n" +
                             "            setDaemon(true);\n" +
                             "            if (in instanceof BufferedInputStream) {\n" +
                             "                bis = (BufferedInputStream) in;\n" +
                             "            } else {\n" +
                             "                bis = new BufferedInputStream(in);\n" +
                             "            }\n" +
                             "            this.out = out;\n" +
                             "            this.closeOutOnExit = closeOutOnExit;\n" +
                             "        }\n" +
                             "    }";
    options.setToReformatAccordingToStyle(true);
    assertEquals("Constructor replacement", expectedResult5, replacer.testReplace(s13, s14, s15, options));
    options.setToReformatAccordingToStyle(false);

    String s16 = "public class A {}\n" +
                 "final class B {}";
    String s17 = "class 'A { '_Other* }";
    String s17_2 = "class 'A { private Log log = LogFactory.createLog(); '_Other* }";
    String s18 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";
    String s18_2 = "class $A$ { $Other$ }";

    String expectedResult6 = "public class A { private Log log = LogFactory.createLog();  }\n" +
                             "final class B { private Log log = LogFactory.createLog();  }";
    assertEquals("Modifier list for class", expectedResult6, replacer.testReplace(s16, s17, s18, options));

    String expectedResult7 = "public class A {  }\n" +
                             "final class B {  }";
    assertEquals("Removing field", expectedResult7, replacer.testReplace(expectedResult6, s17_2, s18_2, options));

    String s19 = "public class A extends Object implements Cloneable {}\n";
    String s20 = "class 'A { '_Other* }";
    String s21 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";

    String expectedResult8 = "public class A extends Object implements Cloneable { private Log log = LogFactory.createLog();  }\n";
    assertEquals("Extends / implements list for class", expectedResult8, replacer.testReplace(s19, s20, s21, options));

    String s22 = "public class A<T> { int Afield; }\n";
    String s23 = "class 'A { '_Other* }";
    String s24 = "class $A$ { private Log log = LogFactory.createLog(); $Other$ }";

    String expectedResult9 = "public class A<T> { private Log log = LogFactory.createLog(); int Afield; }\n";
    assertEquals("Type parameters for the class", expectedResult9, replacer.testReplace(s22, s23, s24, options));

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

    assertEquals(
      "Replacing dcl with saving access modifiers",
      expectedResult10,
      replacer.testReplace(s25,s26,s27,options)
    );

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

    assertEquals(
      "Complex class replacement",
      expectedResult11,
      replacer.testReplace(s28,s29,s30,options)
    );

    String s31 = "class A {\n" +
                 "  int a; // comment\n" +
                 "  char b;\n" +
                 "  int c; // comment2\n" +
                 "}";

    String s32 = "'_Type 'Variable = '_Value?; //'_Comment";
    String s33 = "/**$Comment$*/\n" +
                 "$Type$ $Variable$ = $Value$;";

    String expectedResult12 = "    class A {\n" +
                              "        /**\n" +
                              "         * comment\n" +
                              "         */\n" +
                              "        int a;\n" +
                              "        char b;\n" +
                              "        /**\n" +
                              "         * comment2\n" +
                              "         */\n" +
                              "        int c;\n" +
                              "    }";
    options.setToReformatAccordingToStyle(true);
    assertEquals(
      "Replacing comments with javadoc for fields",
      expectedResult12,
      replacer.testReplace(s31,s32,s33,options)
    );
    options.setToReformatAccordingToStyle(false);

    String s34 = "/**\n" +
                 " * This interface stores XXX\n" +
                 " * <p/>\n" +
                 " */\n" +
                 "public interface X {\n" +
                 "    public static final String HEADER = Headers.HEADER;\n" +
                 "\n" +
                 "}";

    String s35 = "public interface 'MessageInterface {\n" +
                 "    public static final String '_X = '_VALUE;\n" +
                 "    'blah*" +
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

    assertEquals(
      "Replacing interface with interface, saving comments properly",
      expectedResult13,
      replacer.testReplace(s34,s35,s36,options, true)
    );
  }

  public void testClassReplacement3() {
    if (true) return;
    String s37 = "class A { int a = 1; void B() {} int C(char ch) { int z = 1; } int b = 2; }";

    String s38 = "class 'A { 'T* 'M*('PT* 'PN*) { 'S*; } 'O* }";
    String s39 = "class $A$ { $T$ $M$($PT$ $PN$) { System.out.println(\"$M$\"); $S$; } $O$ }";

    String expectedResult14 = "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";
    String expectedResult14_2 = "class A { int a = 1; void B( ) { System.out.println(\"B\");  } int C(char ch) { System.out.println(\"C\"); int z = 1; } int b = 2;}";

    assertEquals(
      "Multiple methods replacement",
      expectedResult14,
      replacer.testReplace(s37,s38,s39,options, true)
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
                            "  public int b ;\n" +
                            "  private int c = 2;\n" +
                            "}";

    assertEquals(
      "Multiple fields replacement",
      expectedResult,
      replacer.testReplace(s1,s2,s3,options, true)
    );
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

    assertEquals(
      "Not preserving comment if it is present",
      expectedResult,
      replacer.testReplace(s1,s2,s3,options, true)
    );
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
    String s1_2 = "public class X {\n" +
                "   /**\n" +
                "    * zzz\n" +
                "    */\n" +
                "   private void f(int i) {\n" +
                "       int a = 1;\n" +
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
                            "   private void f(int i ){//s\n" +
                            "}\n" +
                            "}";

    assertEquals(
      "Correct class replacement",
      expectedResult,
      replacer.testReplace(s1,s2,s3,options)
    );

    String expectedResult2 = "public class X {\n" +
                            "   /**\n" +
                            "    * ppp\n" +
                            "    */\n" +
                            "   private void f(int i ){int a = 1;\n" +
                            "       //s\n" +
                            "}\n" +
                            "}";

    assertEquals(
      "Correct class replacement, 2",
      expectedResult2,
      replacer.testReplace(s1_2,s2,s3,options)
    );
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
                "   /** My Comment */ int a = 3; // aaa\n" +
                "   // bbb\n" +
                "   long c = 2;\n" +
                "   void f() {\n" +
                "   }\n" +
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
                            "// bbb\n" +
                            "   long c = 2;\n" +
                            "void f() {\n" +
                            "   }\n" +
                            "}";

    assertEquals("Class with comment replacement", expectedResult, replacer.testReplace(s1,s2,s3,options,true));
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

    assertEquals("Class field replacement with simple pattern",
                 expectedResult, replacer.testReplace(s1,s2,s3,options,true));
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
    assertEquals("Class replacement 9", expectedResult, replacer.testReplace(s1,s2,s3,options,true));
  }

  public void testReplaceReturnWithArrayInitializer() {
    String searchIn = "return ( new String[]{CoreVars.CMUAudioPort + \"\"} );";
    String searchFor = "return ( 'A );";
    String replaceBy = "return $A$;";
    String expectedResult = "return new String[]{CoreVars.CMUAudioPort + \"\"};";

    assertEquals("ReplaceReturnWithArrayInitializer", expectedResult, replacer.testReplace(searchIn,searchFor,replaceBy,options));
  }

  public void _testClassReplacement10() throws IOException {
    String s1 = loadFile("before2.java");
    String s2 = "class '_Class {\n" +
                "  '_ReturnType+ '_MethodName+('_ParameterType* '_Parameter*){\n" +
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
    assertEquals("Class replacement 10", expectedResult, replacer.testReplace(s1,s2,s3,options,true));
  }

  public void testCatchReplacement() throws Exception {
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
    assertEquals("Catch replacement by block", expectedResult, replacer.testReplace(s1,s2,s3,options));
    options.setToReformatAccordingToStyle(false);

  }

  public void testSavingAccessModifiersDuringClassReplacement() {
    String s43 = "public @Deprecated class Foo implements Comparable<Foo> {\n  int x;\n  void m(){}\n }";
    String s44 = "class 'Class implements '_Interface { '_Content* }";
    String s45 = "@MyAnnotation\n" +
                 "class $Class$ implements $Interface$ {$Content$}";
    String expectedResult16 = "@MyAnnotation public @Deprecated\n" +
                              "class Foo implements Comparable<Foo> {int x;\n" +
                              "void m(){}}";

    assertEquals(
      "Preserving var modifiers and generic information in type during replacement",
      expectedResult16,
      replacer.testReplace(s43, s44, s45, options, true)
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
    assertEquals("No illegal modifier combinations during replacement", expected1, replacer.testReplace(in1, what1, by1, options));
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
      replacer.testReplace(s43, s44, s45, options, true)
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
                 replacer.testReplace(in, what, by, options));
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
      expectedResult15, replacer.testReplace(s40,s41,s42,options, true));

    String s46 = "class Foo { int xxx; void foo() { assert false; } void yyy() {}}";
    String s47 = "class '_Class { void '_foo:[regex( foo )](); }";
    String s48 = "class $Class$ { void $foo$(int a); }";
    String expectedResult17 = "class Foo { int xxx; void foo(int a) { assert false; } void yyy() {}}";

    assertEquals(
      "Preserving method bodies",
      expectedResult17,
      replacer.testReplace(s46,s47,s48,options, true)
    );
  }

  public void testReplaceExceptions() {
    String s1 = "a=a;";
    String s2 = "'a";
    String s3 = "$b$";

    try {
      replacer.testReplace(s1,s2,s3,options);
      assertTrue("Undefined replace variable is not checked",false);
    } catch(UnsupportedPatternException ex) {

    }

    String s4 = "a=a;";
    String s5 = "a=a;";
    String s6 = "a=a";

    try {
      replacer.testReplace(s4,s5,s6,options);
      assertTrue("Undefined no ; in replace",false);
    } catch(UnsupportedPatternException ex) {
    }

    try {
      replacer.testReplace(s4,s6,s5,options);
      assertTrue("Undefined no ; in search",false);
    } catch(UnsupportedPatternException ex) {
    }
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

    assertEquals("Replace in anonymous class parameter", expectedResult, replacer.testReplace(s1, s2, s3, options));
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
    assertEquals("Removing comments", expectedResult, replacer.testReplace(s1,s2,s3,options));
    options.setToReformatAccordingToStyle(false);


    String expectedResult2 = "class A {\n" +
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
                             "    a = 1;\n" +
                             "  }\n" +
                             "}";

    assertEquals("Removing comments", expectedResult2, replacer.testReplace(s1,s2_2,s3,options));
  }

  public void testTryCatchInLoop() throws Exception {
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

    assertEquals("Replacing try/catch in loop", expectedResult, replacer.testReplace(code,toFind,replacement,options));
  }

  public void testUseStaticImport() {
    final String in = "class X {{ Math.abs(-1); }}";
    final String what = "Math.abs('a)";
    final String by = "Math.abs($a$)";
    final boolean save = options.isToUseStaticImport();
    options.setToUseStaticImport(true);
    try {
      final String expected = "import static java.lang.Math.abs;class X {{ abs(-1); }}";
      assertEquals("Replacing with static import", expected, replacer.testReplace(in, what, by, options, true));

      final String in2 = "class X { void m(java.util.Random r) { Math.abs(r.nextInt()); }}";
      final String expected2 = "import static java.lang.Math.abs;class X { void m(java.util.Random r) { abs(r.nextInt()); }}";
      assertEquals("don't add broken static imports", expected2, replacer.testReplace(in2, what, by, options, true));

      final String by2 = "new java.util.Map.Entry() {}";
      final String expected3 = "import static java.util.Map.Entry;class X {{ new Entry() {}; }}";
      assertEquals("", expected3, replacer.testReplace(in, what, by2, options, true));

      final String in3 = "import java.util.Collections;" +
                         "class X {" +
                         "  void m() {" +
                         "    System.out.println(Collections.<String>emptyList());" +
                         "  }" +
                         "}";
      final String what3 = "'_q.'_method:[regex( println )]('a)";
      final String by3 = "$q$.$method$($a$)";
      final String expected4 = "import java.util.Collections;" +
                               "import static java.lang.System.out;" +
                               "class X {" +
                               "  void m() {" +
                               "    out.println(Collections.<String>emptyList());" +
                               "  }" +
                               "}";
      assertEquals("don't break references with type parameters", expected4, replacer.testReplace(in3, what3, by3, options, true));

      final String in4 = "import java.util.Collections;\n" +
                         "public class X {\n" +
                         "    void some() {\n" +
                         "        System.out.println(1);\n" +
                         "        boolean b = Collections.eq(null, null);\n" +
                         "    }\n" +
                         "}";
      final String what4 = "System.out.println(1);";
      final String by4 = "System.out.println(2);";
      final String expected5 = "import java.util.Collections;import static java.lang.System.out;\n" +
                               "public class X {\n" +
                               "    void some() {\n" +
                               "        out.println(2);\n" +
                               "        boolean b = Collections.eq(null, null);\n" +
                               "    }\n" +
                               "}";
      assertEquals("don't add static import to inaccessible members", expected5, replacer.testReplace(in4, what4, by4, options, true));
    } finally {
      options.setToUseStaticImport(save);
    }
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
      final String expected = "import static java.lang.Math.*;class ImportTest {{\n" +
                              "    abs(-0.5);\n" +
                              "    sin(0.5);\n" +
                              "    max(1,2);\n" +
                              "}}";
      assertEquals("Replacing with static star import", expected, replacer.testReplace(in, what, by, options, true));
    } finally {
      options.setToUseStaticImport(save);
    }
  }

  public void testReformatAndShortenClassRefPerformance() throws IOException {
    final String testName = getTestName(false);
    final String ext = "java";
    final String message = "Reformat And Shorten Class Ref Performance";

    options.setToReformatAccordingToStyle(true);
    options.setToShortenFQN(true);

    try {
      PlatformTestUtil.startPerformanceTest("SSR", 3500, new ThrowableRunnable() {
                                              public void run() {
                                                doTest(testName, ext, message);
                                              }
                                            }
      ).useLegacyScaling().assertTiming();
    } finally {
      options.setToReformatAccordingToStyle(false);
      options.setToShortenFQN(false);
    }
  }

  private void doTest(final String testName, final String ext, final String message) {
    try {
      String source = loadFile(testName + "_source." + ext);
      String pattern = loadFile(testName + "_pattern." + ext);
      String replacement = loadFile(testName + "_replacement." + ext);
      String expected = loadFile(testName + "_result." + ext);

      assertEquals(message, expected, replacer.testReplace(source,pattern,replacement,options));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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

    assertEquals(expected, replacer.testReplace(s1,s2,replacement,options));
    assertEquals(expected, replacer.testReplace(s1,s2_2,replacement,options));
    assertEquals(expected, replacer.testReplace(s1,s2_3,replacement,options));
    assertEquals(expected, replacer.testReplace(s1,s2_4,replacement,options));
  }

  public void testLeastSurprise2() {
    String s1 = "class B { int s(int a) { a = 1; a = 2; c(a); } }";
    String s2 = "a";
    String replacement = "a2";
    String expected = "class B { int s(int a2) { a2 = 1; a2 = 2; c(a2); } }";

    assertEquals(expected, replacer.testReplace(s1,s2,replacement,options));
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

    assertEquals(expected, replacer.testReplace(s1,s2,replacement,options));

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
                 expected1, replacer.testReplace(in1, what1, by1, options));

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
                 replacer.testReplace(in2, what2, by2, options));
  }

  public void testReplaceExtraSemicolon() {
    String s1 = "try {\n" +
                "      String[] a = {\"a\"};\n" +
                "      System.out.println(\"blah\");\n" +
                "} finally {\n" +
                "}\n";
    String s2 = "try {\n" + " 'statement*;\n" + "} finally {\n" + "  \n" + "}";
    String replacement = "$statement$;";
    String expected = "String[] a = {\"a\"};\n" +
                "      System.out.println(\"blah\");\n";

    assertEquals(expected, replacer.testReplace(s1,s2,replacement,options));

    String s1_2 = "try {\n" +
                  "    if (args == null) return ;\n" +
                  "    while(true) return ;\n" +
                  "    System.out.println(\"blah2\");\n" +
                  "} finally {\n" +
                  "}";
    String expected_2 = "if (args == null) return ;\n" +
                  "    while(true) return ;\n" +
                  "    System.out.println(\"blah2\");";

    assertEquals(expected_2, replacer.testReplace(s1_2,s2,replacement,options));

    String s1_3 = "{\n" +
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
    assertEquals(expected_3, replacer.testReplace(s1_3,s2,replacement,options));

    String s1_4 = "{\n" +
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
    assertEquals(expected_4, replacer.testReplace(s1_4,s2,replacement,options));
  }

  public void testReplaceFinalModifier() throws Exception {
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

    assertEquals(expected, replacer.testReplace(s1,s2,s3,options));
  }

  public void testKeepUnmatchedModifiers() {
    final String in = "class X {" +
                      "  private static final int foo = 1;" +
                      "}";
    final String expected = "class X {" +
                            "  protected static final int foo = 1;" +
                            "}";

    assertEquals(expected, replacer.testReplace(in, "private '_Type '_field = '_init;", "protected $Type$ $field$ = $init$;", options));
  }

  public void testRemovingRedundancy() throws Exception {
    String s1 = "int a = 1;\n" +
                "a = 2;\n" +
                "int b = a;\n" +
                "b2 = 3;";
    String s2 = "int '_a = '_i;\n" +
                "'_st*;\n" +
                "'_a = '_c;";
    String s3 = "$st$;\n" +
                "$c$ = $i$;";

    String expected = "2 = 1;\nint b = a;\nb2 = 3;";

    assertEquals(expected, replacer.testReplace(s1,s2,s3,options));

    String s2_2 = "int '_a = '_i;\n" +
                  "'_st*;\n" +
                  "int '_c = '_a;";
    String s3_2 = "$st$;\n" +
                  "int $c$ = $i$;";
    String expected_2 = "a = 2;\nint b = 1;\nb2 = 3;";

    assertEquals(expected_2, replacer.testReplace(s1,s2_2,s3_2,options));
  }

  public void testReplaceWithEmptyString() {
    String source = "public class Peepers {\n    public long serialVersionUID = 1L;    \n}";
    String search = "long serialVersionUID = $value$;";
    String replace = "";
    String expectedResult = "public class Peepers {    \n}";

    assertEquals(expectedResult, replacer.testReplace(source, search, replace, options, true));
  }

  public void testReplaceMultipleFieldsInSingleDeclaration() {
    String source = "abstract class MyClass implements java.util.List {\n  private String a, b;\n}";
    String search = "class 'Name implements java.util.List {\n  '_ClassContent*\n}";
    String replace = "class $Name$ {\n  $ClassContent$\n}";
    String expectedResult = "abstract class MyClass {\n  private String a,b;\n}";

    assertEquals(expectedResult, replacer.testReplace(source, search, replace, options, true));
  }

  public void testReplaceInImplementsList() {
    String source = "import java.io.Externalizable;\n" +
                    "import java.io.Serializable;\n" +
                    "abstract class MyClass implements Serializable, java.util.List, Externalizable {}";
    String search = "class 'TestCase implements java.util.List, '_others* {\n    '_MyClassContent\n}";
    String replace = "class $TestCase$ implements $others$ {\n    $MyClassContent$\n}";
    String expectedResult = "import java.io.Externalizable;\n" +
                            "import java.io.Serializable;\n" +
                            "abstract class MyClass implements Externalizable,Serializable {\n    \n}";

    assertEquals(expectedResult, replacer.testReplace(source, search, replace, options, true));
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
                            "public void foo() {\n" +
                            "    }\n" +
                            "    void bar() {}\n" +
                            "}";

    assertEquals(expectedResult, replacer.testReplace(source, search, replace, options, true));
  }

  public void testReplaceAnnotation() {
    String in = "@SuppressWarnings(\"ALL\")\n" +
                "public class A {}";
    String what = "@SuppressWarnings(\"ALL\")";

    final String by1 = "";
    assertEquals("public class A {}", replacer.testReplace(in, what, by1, options, false));

    final String by2 = "@SuppressWarnings(\"NONE\") @Deprecated";
    assertEquals("@SuppressWarnings(\"NONE\") @Deprecated\n" +
                 "public class A {}", replacer.testReplace(in, what, by2, options, false));

    final String in2 = "class X {" +
                 "  @SuppressWarnings(\"unused\") String s;" +
                 "}";
    final String what2 = "@SuppressWarnings(\"unused\") String '_s;";
    final String by3 = "@SuppressWarnings({\"unused\", \"other\"}) String $s$;";
    assertEquals("class X {" +
                 "  @SuppressWarnings({\"unused\", \"other\"}) String s;" +
                 "}", replacer.testReplace(in2, what2, by3, options, false));

  }

  public void testReplacePolyadicExpression() {
    final String in1 = "class A {" +
                      "  int i = 1 + 2 + 3;" +
                      "}";
    final String what1 = "1 + '_a+";

    final String by1 = "4";
    assertEquals("class A {  int i = 4;}", replacer.testReplace(in1, what1, by1, options, false));

    final String by2 = "$a$";
    assertEquals("class A {  int i = 2+3;}", replacer.testReplace(in1, what1, by2, options, false));

    final String by3 = "$a$+4";
    assertEquals("class A {  int i = 2+3+4;}", replacer.testReplace(in1, what1, by3, options, false));

    final String what2 = "1 + 2 + 3 + '_a*";
    final String by4 = "1 + 3 + $a$";
    assertEquals("class A {  int i = 1 + 3;}", replacer.testReplace(in1, what2, by4, options, false));

    final String by5 = "$a$ + 1 + 3";
    assertEquals("class A {  int i = 1 + 3;}", replacer.testReplace(in1, what2, by5, options, false));

    final String by6 = "1 + $a$ + 3";
    assertEquals("class A {  int i = 1  + 3;}", replacer.testReplace(in1, what2, by6, options, false));

    final String in2 = "class A {" +
                       "  boolean b = true && true;" +
                       "}";
    final String what3 = "true && true && '_a*";
    final String by7 = "true && true && $a$";
    assertEquals("class A {  boolean b = true && true;}", replacer.testReplace(in2, what3, by7, options, false));

    final String by8 = "$a$ && true && true";
    assertEquals("class A {  boolean b = true && true;}", replacer.testReplace(in2, what3, by8, options, false));

  }

  public void testReplaceAssert() {
    final String in = "class A {" +
                      "  void m(int i) {" +
                      "    assert 10 > i;" +
                      "  }" +
                      "}";

    final String what = "assert '_a > '_b : '_c?;";
    final String by = "assert $b$ < $a$ : $c$;";
    assertEquals("class A {  void m(int i) {    assert i < 10 ;  }}", replacer.testReplace(in, what, by, options, false));
  }

  public void testReplaceMultipleVariablesInOneDeclaration() {
    final String in = "class A {" +
                      "  private int i, j, k;" +
                      "  void m() {" +
                      "    int i,j,k;" +
                      "  }" +
                      "}";
    final String what1 = "int '_i+;";
    final String by1 = "float $i$;";
    assertEquals("class A {  private float i,j,k;  void m() {    float i,j,k;  }}", replacer.testReplace(in, what1, by1, options));

    final String what2 = "int '_a, '_b, '_c = '_d?;";
    final String by2 = "float $a$, $b$, $c$ = $d$;";
    assertEquals("class A {  private float i, j, k ;  void m() {    float i, j, k ;  }}", replacer.testReplace(in, what2, by2, options));
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
    final ReplacementVariableDefinition variable = new ReplacementVariableDefinition();
    variable.setName("newarg");
    variable.setScriptCodeConstraint("arg.collect { \"(String)\" + it.getText() }.join(',')");
    options.addVariableDefinition(variable);

    final String expected = "class A {\n" +
                            "  void method(Object... os) {}\n" +
                            "  void f(Object a, Object b, Object c) {\n" +
                            "    method((String)a,(String)b,(String)c,(String)\"one\" + \"two\");\n" +
                            "    method((String)a);\n" +
                            "  }\n" +
                            "}";
    assertEquals(expected, replacer.testReplace(in, what, by, options));

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
                 "}\n", replacer.testReplace(in, what, by, options));

    final String what2 = "void '_a:[regex( test.* )]();";
    final String by2 = "@org.junit.Test void $a$();";
    assertEquals("class A extends TestCase {\n" +
                 "  @org.junit.Test void testOne() {\n" +
                 "    System.out.println();\n" +
                 "  }\n" +
                 "}\n",
                 replacer.testReplace(in, what2, by2, options));
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
                 replacer.testReplace(in, what, by, options));
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
                 replacer.testReplace(in, what, by, options));
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
                 replacer.testReplace(in, what, by, options));

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
                 replacer.testReplace(in2, what, by, options));
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
                 replacer.testReplace(in, what, by, options));
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
                 replacer.testReplace(in, what, by, options, true));
  }
}