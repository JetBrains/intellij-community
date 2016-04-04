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
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
public class ExpressionsParsingTest extends GroovyParsingTestCase {
  @Override
  public String getBasePath() {
    return super.basePath + "expressions";
  }

  public void testarguments$carg1() throws Throwable { doTest(); }

  public void testarguments$carg2() throws Throwable { doTest(); }

  public void testarguments$carg3() throws Throwable { doTest(); }

  public void testarguments$cargs1() throws Throwable { doTest(); }

  public void testarguments$cargs2() throws Throwable { doTest(); }

  public void testarguments$cargs3() throws Throwable { doTest(); }

  public void testarithmetic$add1() throws Throwable { doTest(); }

  public void testarithmetic$add2() throws Throwable { doTest(); }

  public void testarithmetic$addbug1() throws Throwable { doTest(); }

  public void testarithmetic$arif1() throws Throwable { doTest(); }

  public void testarithmetic$mul1() throws Throwable { doTest(); }

  public void testarithmetic$mul2() throws Throwable { doTest(); }

  public void testarithmetic$mul3() throws Throwable { doTest(); }

  public void testarithmetic$post1() throws Throwable { doTest(); }

  public void testarithmetic$sh1() throws Throwable { doTest(); }

  public void testarithmetic$shift5() throws Throwable { doTest(); }

  public void testarithmetic$shift6() throws Throwable { doTest(); }

  public void testarithmetic$un1() throws Throwable { doTest(); }

  public void testass1() throws Throwable { doTest(); }

  public void testass2() throws Throwable { doTest(); }

  public void testass3() throws Throwable { doTest(); }

  public void testclosures$appended() throws Throwable { doTest(); }

  public void testclosures$closparam1() throws Throwable { doTest(); }

  public void testclosures$closparam2() throws Throwable { doTest(); }

  public void testclosures$closparam3() throws Throwable { doTest(); }

  public void testclosures$closparam4() throws Throwable { doTest(); }

  public void testclosures$closparam5() throws Throwable { doTest(); }

  public void testclosures$closparam6() throws Throwable { doTest(); }

  public void testclosures$final_error() throws Throwable { doTest(); }

  public void testclosures$param6() throws Throwable { doTest(); }

  public void testclosures$param7() throws Throwable { doTest(); }

  public void testconditional$con1() throws Throwable { doTest(); }

  public void testconditional$con2() throws Throwable { doTest(); }

  public void testconditional$elvis1() throws Throwable { doTest(); }

  public void testconditional$elvis2() throws Throwable { doTest(); }

  public void testerrors$err_final() throws Throwable { doTest(); }

  public void testgstring$daniel_sun() throws Throwable { doTest(); }

  public void testgstring$gravy16532() throws Throwable { doTest("gstring/gravy-1653-2.test"); }

  public void testgstring$grvy1653() throws Throwable { doTest("gstring/grvy-1653.test"); }

  public void testgstring$gstr3() throws Throwable { doTest(); }

  public void testgstring$standTrooper() throws Throwable { doTest(); }

  public void testgstring$str1() throws Throwable { doTest(); }

  public void testgstring$str2() throws Throwable { doTest(); }

  public void testgstring$str3() throws Throwable { doTest(); }

  public void testgstring$str4() throws Throwable { doTest(); }

  public void testgstring$str5() throws Throwable { doTest(); }

  public void testgstring$str6() throws Throwable { doTest(); }

  public void testgstring$str7() throws Throwable { doTest(); }

  public void testgstring$str8() throws Throwable { doTest(); }

  public void testgstring$str_error1() throws Throwable { doTest(); }

  public void testgstring$str_error2() throws Throwable { doTest(); }

  public void testgstring$str_error3() throws Throwable { doTest(); }

  public void testgstring$str_error4() throws Throwable { doTest(); }

  public void testgstring$str_error5() throws Throwable { doTest(); }

  public void testgstring$str_error6() throws Throwable { doTest(); }

  public void testgstring$str_error7() throws Throwable { doTest(); }

  public void testgstring$str_error8() throws Throwable { doTest(); }

  public void testgstring$triple$triple1() throws Throwable { doTest(); }

  public void testgstring$triple$triple2() throws Throwable { doTest(); }

  public void testgstring$triple$triple3() throws Throwable { doTest(); }

  public void testgstring$triple$quote_and_slash() throws Throwable { doTest(); }

  public void testgstring$ugly_lexer() throws Throwable { doTest(); }

  public void testgstring$this() {doTest()}

  public void testmapLiteral() throws Throwable { doTest(); }

  public void testnew$arr_decl() throws Throwable { doTest(); }
  public void testnew$emptyTypeArgs() { doTest() }
//  public void testnew$new1() throws Throwable { doTest(); }
  public void testanonymous$anonymous() throws Throwable { doTest(); }

  public void testnumbers() throws Throwable { doTest(); }

  public void testparenthed$exprInParenth() throws Throwable { doTest(); }

  public void testparenthed$paren1() throws Throwable { doTest(); }

  public void testparenthed$paren2() throws Throwable { doTest(); }

  public void testparenthed$paren3() throws Throwable { doTest(); }

  public void testparenthed$paren4() throws Throwable { doTest(); }

  public void testparenthed$paren5() throws Throwable { doTest(); }

  public void testparenthed$paren6() throws Throwable { doTest(); }

  public void testpath$method$ass4() throws Throwable { doTest(); }

  public void testpath$method$clazz1() throws Throwable { doTest(); }

  public void testpath$method$clazz2() throws Throwable { doTest(); }

  public void testpath$method$clos1() throws Throwable { doTest(); }

  public void testpath$method$clos2() throws Throwable { doTest(); }

  public void testpath$method$clos3() throws Throwable { doTest(); }

  public void testpath$method$clos4() throws Throwable { doTest(); }

  public void testpath$method$ind1() throws Throwable { doTest(); }

  public void testpath$method$ind2() throws Throwable { doTest(); }

  public void testpath$method$ind3() throws Throwable { doTest(); }

  public void testpath$method$method1() throws Throwable { doTest(); }

  public void testpath$method$method10() throws Throwable { doTest(); }

  public void testpath$method$method11() throws Throwable { doTest(); }

  public void testpath$method$method12() throws Throwable { doTest(); }

  public void testpath$method$method13() throws Throwable { doTest(); }

  public void testpath$method$method2() throws Throwable { doTest(); }

  public void testpath$method$method3() throws Throwable { doTest(); }

  public void testpath$method$method4() throws Throwable { doTest(); }

  public void testpath$method$method5() throws Throwable { doTest(); }

  public void testpath$method$method6() throws Throwable { doTest(); }

  public void testpath$method$method7() throws Throwable { doTest(); }

  public void testpath$method$method8() throws Throwable { doTest(); }

  public void testpath$method$method9() throws Throwable { doTest(); }

  public void testpath$path1() throws Throwable { doTest(); }

  public void testpath$path13() throws Throwable { doTest(); }

  public void testpath$path14() throws Throwable { doTest(); }

  public void testpath$path2() throws Throwable { doTest(); }

  public void testpath$path3() throws Throwable { doTest(); }

  public void testpath$path4() throws Throwable { doTest(); }

  public void testpath$path5() throws Throwable { doTest(); }

  public void testpath$path6() throws Throwable { doTest(); }

  public void testpath$path7() throws Throwable { doTest(); }

  public void testpath$path8() throws Throwable { doTest(); }

  public void testpath$path9() throws Throwable { doTest(); }

  public void testpath$path10() throws Throwable { doTest(); }

  public void testpath$regexp() { doTest() }

  public void testpath$typeVsExpr() { doTest(); }

  public void testreferences$ref1() throws Throwable { doTest(); }
  public void testreferences$ref2() throws Throwable { doTest(); }
  public void testreferences$ref3() throws Throwable { doTest(); }
  public void testreferences$ref4() throws Throwable { doTest(); }
  public void testreferences$ref5() throws Throwable { doTest(); }
  public void testreferences$ref6() throws Throwable { doTest(); }
  public void testreferences$ref7() throws Throwable { doTest(); }
  public void testreferences$ref8() throws Throwable { doTest(); }
  public void testreferences$emptyTypeArgs() { doTest() }

  public void testregex$chen() throws Throwable { doTest(); }

  public void testregex$GRVY1509err() throws Throwable { doTest("regex/GRVY-1509err.test"); }

  public void testregex$GRVY1509norm() throws Throwable { doTest("regex/GRVY-1509norm.test"); }

  public void testregex$GRVY1509test() throws Throwable { doTest("regex/GRVY-1509test.test"); }

  public void testregex$regex1() throws Throwable { doTest(); }

  public void testregex$regex10() throws Throwable { doTest(); }

  public void testregex$regex11() throws Throwable { doTest(); }

  public void testregex$regex12() throws Throwable { doTest(); }

  public void testregex$regex13() throws Throwable { doTest(); }

  public void testregex$regex14() throws Throwable { doTest(); }

  public void testregex$regex15() throws Throwable { doTest(); }

  public void testregex$regex16() throws Throwable { doTest(); }

  public void testregex$regex17() throws Throwable { doTest(); }

  public void testregex$regex18() throws Throwable { doTest(); }

  public void testregex$regex19() throws Throwable { doTest(); }

  public void testregex$regex2() throws Throwable { doTest(); }

  public void testregex$regex20() throws Throwable { doTest(); }

  public void testregex$regex21() throws Throwable { doTest(); }

  public void testregex$regex22() throws Throwable { doTest(); }

  public void testregex$regex23() throws Throwable { doTest(); }

  public void testregex$regex24() throws Throwable { doTest(); }

  public void testregex$regex25() throws Throwable { doTest(); }

  public void testregex$regex3() throws Throwable { doTest(); }

  public void testregex$regex33() throws Throwable { doTest(); }

  public void testregex$regex4() throws Throwable { doTest(); }

  public void testregex$regex5() throws Throwable { doTest(); }

  public void testregex$regex6() throws Throwable { doTest(); }

  public void testregex$regex7() throws Throwable { doTest(); }

  public void testregex$regex8() throws Throwable { doTest(); }

  public void testregex$regex9() throws Throwable { doTest(); }

  public void testregex$regex_begin() throws Throwable { doTest(); }

  public void testregex$regex_begin2() throws Throwable { doTest(); }

  public void testregex$multiLineSlashy() throws Throwable { doTest(); }

  public void testregex$dollarSlashy() throws Throwable { doTest(); }

  public void testregex$dollarSlashy2() throws Throwable { doTest(); }

  public void testregex$dollarSlashy3() throws Throwable { doTest(); }

  public void testregex$dollarSlashy4() throws Throwable { doTest(); }

  public void testregex$dollarSlashy5() throws Throwable { doTest(); }

  public void testregex$dollarSlashy6() throws Throwable { doTest(); }

  public void testregex$dollarSlashy7() throws Throwable { doTest(); }

  public void testregex$dollarSlashy8() throws Throwable { doTest(); }

  public void testregex$dollarSlashy9() throws Throwable { doTest(); }

  public void testregex$dollarSlashyCode() throws Throwable { doTest(); }

  public void testregex$dollarSlashyCodeUnfinished() throws Throwable { doTest(); }

  public void testregex$dollarSlashyEof() throws Throwable { doTest(); }

  public void testregex$dollarSlashyRegex() throws Throwable { doTest(); }

  public void testregex$dollarSlashyRegexFinishedTwice() throws Throwable { doTest(); }

  public void testregex$dollarSlashyRegexUnfinished() throws Throwable { doTest(); }

  public void testregex$dollarSlashyUnfinished() throws Throwable { doTest(); }

  public void testregex$dollarSlashyWindowsPaths() throws Throwable { doTest(); }

  public void testregex$dollarSlashyXml() throws Throwable { doTest(); }

  public void testregex$dollarSlashyDouble() throws Throwable { doTest(); }

  public void testregex$dollarSlashyTriple() throws Throwable { doTest(); }

  public void testregex$dollarSlashyUltimate() { doTest() }

  public void testrelational$eq1() throws Throwable { doTest(); }

  public void testrelational$inst0() throws Throwable { doTest(); }

  public void testrelational$inst1() throws Throwable { doTest(); }

  public void testrelational$inst2() throws Throwable { doTest(); }

  public void testrelational$rel1() throws Throwable { doTest(); }

  public void testspecial$grvy1173() throws Throwable { doTest(); }

  public void testspecial$list1() throws Throwable { doTest(); }

  public void testspecial$list2() throws Throwable { doTest(); }

  public void testspecial$list3() throws Throwable { doTest(); }

  public void testspecial$map1() throws Throwable { doTest(); }

  public void testspecial$map2() throws Throwable { doTest(); }

  public void testspecial$map3() throws Throwable { doTest(); }

  public void testspecial$map4() throws Throwable { doTest(); }

  public void testspecial$map5() throws Throwable { doTest(); }

  public void testspecial$map6() throws Throwable { doTest(); }

  public void testspecial$map7() throws Throwable { doTest(); }

  public void testspecial$map8() throws Throwable { doTest(); }

  public void testspecial$paren13() throws Throwable { doTest(); }

  public void testtypecast$castToObject() throws Throwable { doTest(); }

  public void testtypecast$una1() throws Throwable { doTest(); }

  public void testtypecast$una2() throws Throwable { doTest(); }

  public void testtypecast$una3() throws Throwable { doTest(); }

  public void testtypecast$una4() throws Throwable { doTest(); }

  public void testtypecast$una5() throws Throwable { doTest(); }

  public void testtypecast$una6() throws Throwable { doTest(); }

  public void testtypecast$elvis() throws Throwable { doTest(); }

  public void testtypecast$conditional() throws Throwable { doTest(); }

  public void testAtHang() throws Throwable { doTest(); }

  public void testDollar() throws Throwable { doTest(); }

  public void testNoArrowClosure() throws Throwable { doTest(); }

  public void testNoArrowClosure2() throws Throwable { doTest(); }

  public void testPropertyAccessError() throws Throwable {
    checkParsingByText "a[b{}}", """Groovy script
  Property by index
    Reference expression
      PsiElement(identifier)('a')
    Arguments
      PsiElement([)('[')
      Method call
        Reference expression
          PsiElement(identifier)('b')
        Arguments
          <empty list>
        Closable block
          PsiElement({)('{')
          Parameter list
            <empty list>
          PsiElement(})('}')
      PsiErrorElement:',' or ']' expected
        <empty list>
  PsiErrorElement:Unexpected symbol
    PsiElement(})('}')"""
  }

  public void testthis$qualifiedThis() throws Throwable { doTest(); }

  public void testsuper$qualifiedSuper() throws Throwable { doTest(); }

  public void testthis$this() throws Throwable { doTest(); }

  public void testsuper$super() throws Throwable { doTest(); }

  public void testTripleEqual() throws Exception {
    checkParsingByText "2===3", """Groovy script
  Relational expression
    Literal
      PsiElement(Integer)('2')
    PsiElement(==)('===')
    Literal
      PsiElement(Integer)('3')
"""
  }

  public void testcommandExpr$closureArg() { doTest() }

  public void testcommandExpr$simple() { doTest() }

  public void testcommandExpr$callArg1() { doTest() }

  public void testcommandExpr$callArg2() { doTest() }

  public void testcommandExpr$threeArgs1() { doTest() }

  public void testcommandExpr$threeArgs2() { doTest() }

  public void testcommandExpr$threeArgs3() { doTest() }

  public void testcommandExpr$fourArgs() { doTest() }

  public void testcommandExpr$fiveArgs() { doTest() }

  public void testcommandExpr$multiArgs() { doTest() }

  public void testcommandExpr$RHS() { doTest() }

  public void testcommandExpr$oddArgCount() { doTest() }

  public void testcommandExpr$indexAccess1() { doTest() }

  public void testcommandExpr$indexAccess2() { doTest() }

  public void testcommandExpr$indexAccess3() { doTest() }

  public void testcommandExpr$closureArg2() { doTest() }

  public void testcommandExpr$closureArg3() { doTest() }

  public void testcommandExpr$not() { doTest() }

  public void testcommandExpr$methodCall() { doTest() }

  public void testcommandExpr$indexProperty() { doTest() }

  public void testcommandExpr$instanceof() { doTest() }

  public void testcommandExpr$instanceof2() { doTest() }

  public void testcommandExpr$in() { doTest() }

  public void testcommandExpr$as() { doTest() }

  public void testcommandExpr$arrayAccess() { doTest() }

  public void testcommandExpr$keywords() { doTest() }

  public void testcommandExpr$literalInvoked() { doTest() }

  public void testDiamond() { doTest() }

  void testpath$stringMethodCall1() { doTest() }

  void testpath$stringMethodCall2() { doTest() }

  void testpath$stringMethodCall3() { doTest() }

  void testSpacesInStringAfterSlash() {
    checkParsingByText '''
print 'abc \\ \ncde' ''', '''
Groovy script
  PsiElement(new line)(\'\\n\')
  Call expression
    Reference expression
      PsiElement(identifier)(\'print\')
    PsiWhiteSpace(\' \')
    Command arguments
      Literal
        PsiElement(string)(\'\'abc \\ \\ncde\'\')
  PsiWhiteSpace(\' \')'''
  }

  void testDiamondInPathRefElement() {
    checkParsingByText 'Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>()', '''
Groovy script
  Variable definitions
    Modifiers
      <empty list>
    Type element
      Reference element
        PsiElement(identifier)('Map')
        Type arguments
          PsiElement(<)('<')
          Type element
            Reference element
              PsiElement(identifier)('String')
          PsiElement(,)(',')
          PsiWhiteSpace(' ')
          Type element
            Reference element
              PsiElement(identifier)('String')
          PsiElement(>)('>')
    PsiWhiteSpace(' ')
    Variable
      PsiElement(identifier)('map')
      PsiWhiteSpace(' ')
      PsiElement(=)('=')
      PsiWhiteSpace(' ')
      NEW expression
        PsiElement(new)('new')
        PsiWhiteSpace(' ')
        Reference element
          Reference element
            Reference element
              Reference element
                PsiElement(identifier)('java')
              PsiElement(.)('.')
              PsiElement(identifier)('util')
            PsiElement(.)('.')
            PsiElement(identifier)('concurrent')
          PsiElement(.)('.')
          PsiElement(identifier)('ConcurrentHashMap')
          Type arguments
            PsiElement(<)('<')
            PsiElement(>)('>')
        Arguments
          PsiElement(()('(')
          PsiElement())(')')
'''
  }

  void testNewMethodName() {
    checkParsingByText 'def a = qualifer.new X()', '''
Groovy script
  Variable definitions
    Modifiers
      PsiElement(def)('def')
    PsiWhiteSpace(' ')
    Variable
      PsiElement(identifier)('a')
      PsiWhiteSpace(' ')
      PsiElement(=)('=')
      PsiWhiteSpace(' ')
      Call expression
        Reference expression
          Reference expression
            PsiElement(identifier)('qualifer')
          PsiElement(.)('.')
          PsiElement(new)('new')
        PsiWhiteSpace(' ')
        Command arguments
          Method call
            Reference expression
              PsiElement(identifier)('X')
            Arguments
              PsiElement(()('(')
              PsiElement())(')')'''
  }

  void testRefElementsWithKeywords() {
    checkParsingByText('''\
def a = new def.as.Foo()
def b = new foo.as.in.Foo()
''', '''\
Groovy script
  Variable definitions
    Modifiers
      PsiElement(def)('def')
    PsiWhiteSpace(' ')
    Variable
      PsiElement(identifier)('a')
      PsiWhiteSpace(' ')
      PsiElement(=)('=')
      PsiWhiteSpace(' ')
      NEW expression
        PsiElement(new)('new')
        PsiWhiteSpace(' ')
        Reference element
          Reference element
            Reference element
              PsiElement(def)('def')
            PsiElement(.)('.')
            PsiElement(as)('as')
          PsiElement(.)('.')
          PsiElement(identifier)('Foo')
        Arguments
          PsiElement(()('(')
          PsiElement())(')')
  PsiElement(new line)('\\n')
  Variable definitions
    Modifiers
      PsiElement(def)('def')
    PsiWhiteSpace(' ')
    Variable
      PsiElement(identifier)('b')
      PsiWhiteSpace(' ')
      PsiElement(=)('=')
      PsiWhiteSpace(' ')
      NEW expression
        PsiElement(new)('new')
        PsiWhiteSpace(' ')
        Reference element
          Reference element
            Reference element
              Reference element
                PsiElement(identifier)('foo')
              PsiElement(.)('.')
              PsiElement(as)('as')
            PsiElement(.)('.')
            PsiElement(in)('in')
          PsiElement(.)('.')
          PsiElement(identifier)('Foo')
        Arguments
          PsiElement(()('(')
          PsiElement())(')')
  PsiElement(new line)('\\n')
''')
  }

  public void "test finish argument list on keyword occurrence"() {
    checkParsingByText '''switch (obj) {
      case 1: return bar([param)
      case 3: return bar([param]
      case 2:
        param = param.bar((foo):[bar:goo])
        return param.foo
    }
''', '''\
Groovy script
  Switch statement
    PsiElement(switch)('switch')
    PsiWhiteSpace(' ')
    PsiElement(()('(')
    Reference expression
      PsiElement(identifier)('obj')
    PsiElement())(')')
    PsiWhiteSpace(' ')
    PsiElement({)('{')
    PsiWhiteSpace('\\n      ')
    Case section
      Case label
        PsiElement(case)('case')
        PsiWhiteSpace(' ')
        Literal
          PsiElement(Integer)('1')
        PsiElement(:)(':')
      PsiWhiteSpace(' ')
      RETURN statement
        PsiElement(return)('return')
        PsiWhiteSpace(' ')
        Method call
          Reference expression
            PsiElement(identifier)('bar')
          Arguments
            PsiElement(()('(')
            Generalized list
              PsiElement([)('[')
              Reference expression
                PsiElement(identifier)('param')
              PsiErrorElement:',' or ']' expected
                <empty list>
              PsiElement())(')')
              PsiErrorElement:',' or ']' expected
                <empty list>
    PsiWhiteSpace('\\n      ')
    Case section
      Case label
        PsiElement(case)('case')
        PsiWhiteSpace(' ')
        Literal
          PsiElement(Integer)('3')
        PsiElement(:)(':')
      PsiWhiteSpace(' ')
      RETURN statement
        PsiElement(return)('return')
        PsiWhiteSpace(' ')
        Method call
          Reference expression
            PsiElement(identifier)('bar')
          Arguments
            PsiElement(()('(')
            Generalized list
              PsiElement([)('[')
              Reference expression
                PsiElement(identifier)('param')
              PsiElement(])(']')
            PsiErrorElement:',' or ')' expected
              <empty list>
    PsiWhiteSpace('\\n      ')
    Case section
      Case label
        PsiElement(case)('case')
        PsiWhiteSpace(' ')
        Literal
          PsiElement(Integer)('2')
        PsiElement(:)(':')
      PsiWhiteSpace('\\n        ')
      Assignment expression
        Reference expression
          PsiElement(identifier)('param')
        PsiWhiteSpace(' ')
        PsiElement(=)('=')
        PsiWhiteSpace(' ')
        Method call
          Reference expression
            Reference expression
              PsiElement(identifier)('param')
            PsiElement(.)('.')
            PsiElement(identifier)('bar')
          Arguments
            PsiElement(()('(')
            Named argument
              Argument label
                Parenthesized expression
                  PsiElement(()('(')
                  Reference expression
                    PsiElement(identifier)('foo')
                  PsiElement())(')')
              PsiElement(:)(':')
              Generalized list
                PsiElement([)('[')
                Named argument
                  Argument label
                    PsiElement(identifier)('bar')
                  PsiElement(:)(':')
                  Reference expression
                    PsiElement(identifier)('goo')
                PsiElement(])(']')
            PsiElement())(')')
      PsiErrorElement:';', '}' or new line expected
        <empty list>
      PsiWhiteSpace('\\n        ')
      RETURN statement
        PsiElement(return)('return')
        PsiWhiteSpace(' ')
        Reference expression
          Reference expression
            PsiElement(identifier)('param')
          PsiElement(.)('.')
          PsiElement(identifier)('foo')
    PsiWhiteSpace('\\n    ')
    PsiElement(})('}')
  PsiElement(new line)('\\n')'''
  }

  void testConditionalExpressionWithLineFeed() {
    checkParsingByText('''\
print true ? abc
:cde
''', '''\
Groovy script
  Call expression
    Reference expression
      PsiElement(identifier)('print')
    PsiWhiteSpace(' ')
    Command arguments
      Conditional expression
        Literal
          PsiElement(true)('true')
        PsiWhiteSpace(' ')
        PsiElement(?)('?')
        PsiWhiteSpace(' ')
        Reference expression
          PsiElement(identifier)('abc')
        PsiElement(new line)('\\n')
        PsiElement(:)(':')
        Reference expression
          PsiElement(identifier)('cde')
  PsiElement(new line)('\\n')
''')
  }

  void testspecial$mapHang() { doTest() }
}
