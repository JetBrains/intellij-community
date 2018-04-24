// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class ExpressionsParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "expressions"
  }

  void testarguments$carg1() throws Throwable { doTest() }

  void testarguments$carg2() throws Throwable { doTest() }

  void testarguments$carg3() throws Throwable { doTest() }

  void testarguments$cargs1() throws Throwable { doTest() }

  void testarguments$cargs2() throws Throwable { doTest() }

  void testarguments$cargs3() throws Throwable { doTest() }

  void testarithmetic$add1() throws Throwable { doTest() }

  void testarithmetic$add2() throws Throwable { doTest() }

  void testarithmetic$addbug1() throws Throwable { doTest() }

  void testarithmetic$arif1() throws Throwable { doTest() }

  void testarithmetic$mul1() throws Throwable { doTest() }

  void testarithmetic$mul2() throws Throwable { doTest() }

  void testarithmetic$mul3() throws Throwable { doTest() }

  void testarithmetic$mul4() throws Throwable { doTest() }

  void testarithmetic$post1() throws Throwable { doTest() }

  void testarithmetic$sh1() throws Throwable { doTest() }

  void testarithmetic$shift5() throws Throwable { doTest() }

  void testarithmetic$shift6() throws Throwable { doTest() }

  void testarithmetic$un1() throws Throwable { doTest() }

  void testass1() throws Throwable { doTest() }

  void testass2() throws Throwable { doTest() }

  void testass3() throws Throwable { doTest() }

  void testclosures$appended() throws Throwable { doTest() }

  void testclosures$closparam1() throws Throwable { doTest() }

  void testclosures$closparam2() throws Throwable { doTest() }

  void testclosures$closparam3() throws Throwable { doTest() }

  void testclosures$closparam4() throws Throwable { doTest() }

  void testclosures$closparam5() throws Throwable { doTest() }

  void testclosures$closparam6() throws Throwable { doTest() }

  void testclosures$final_error() throws Throwable { doTest() }

  void testclosures$param6() throws Throwable { doTest() }

  void testclosures$param7() throws Throwable { doTest() }

  void testconditional$con1() throws Throwable { doTest() }

  void testconditional$con2() throws Throwable { doTest() }

  void testconditional$elvis1() throws Throwable { doTest() }

  void testconditional$elvis2() throws Throwable { doTest() }

  void testerrors$err_final() throws Throwable { doTest() }

  void testgstring$daniel_sun() throws Throwable { doTest() }

  void testgstring$gravy16532() throws Throwable { doTest("gstring/gravy-1653-2.test") }

  void testgstring$grvy1653() throws Throwable { doTest("gstring/grvy-1653.test") }

  void testgstring$gstr3() throws Throwable { doTest() }

  void testgstring$standTrooper() throws Throwable { doTest() }

  void testgstring$str1() throws Throwable { doTest() }

  void testgstring$str2() throws Throwable { doTest() }

  void testgstring$str3() throws Throwable { doTest() }

  void testgstring$str4() throws Throwable { doTest() }

  void testgstring$str5() throws Throwable { doTest() }

  void testgstring$str6() throws Throwable { doTest() }

  void testgstring$str7() throws Throwable { doTest() }

  void testgstring$str8() throws Throwable { doTest() }

  void testgstring$str9() throws Throwable { doTest() }

  void teststring$singleQuoted() { doTest() }

  void teststring$tripleSingleQuoted$ok() { doTest() }

  void teststring$tripleSingleQuoted$err0() { doTest() }

  void teststring$tripleSingleQuoted$err1() { doTest() }

  void teststring$tripleSingleQuoted$err2() { doTest() }

  void teststring$tripleSingleQuoted$err3() { doTest() }

  void teststring$tripleSingleQuoted$err4() { doTest() }

  void teststring$tripleSingleQuoted$err5() { doTest() }

  void teststring$tripleSingleQuoted$unfinished0() { doTest() }

  void teststring$tripleSingleQuoted$unfinished1() { doTest() }

  void teststring$tripleSingleQuoted$unfinished2() { doTest() }

  void teststring$tripleSingleQuoted$unfinished3() { doTest() }

  void testgstring$str_error1() throws Throwable { doTest() }

  void testgstring$str_error2() throws Throwable { doTest() }

  void testgstring$str_error3() throws Throwable { doTest() }

  void testgstring$str_error4() throws Throwable { doTest() }

  void testgstring$str_error5() throws Throwable { doTest() }

  void testgstring$str_error6() throws Throwable { doTest() }

  void testgstring$str_error7() throws Throwable { doTest() }

  void testgstring$str_error8() throws Throwable { doTest() }

  void testgstring$triple$triple1() throws Throwable { doTest() }

  void testgstring$triple$triple2() throws Throwable { doTest() }

  void testgstring$triple$triple3() throws Throwable { doTest() }

  void testgstring$triple$triple4() throws Throwable { doTest() }

  void testgstring$triple$quote_and_slash() throws Throwable { doTest() }

  void testgstring$ugly_lexer() throws Throwable { doTest() }

  void testgstring$this() { doTest() }

  void testgstring$newline() { doTest() }

  void testmapLiteral() throws Throwable { doTest() }

  void testnew$arr_decl() throws Throwable { doTest() }

  void testnew$emptyTypeArgs() { doTest() }
//  public void testnew$new1() throws Throwable { doTest(); }
  void testanonymous$anonymous() throws Throwable { doTest() }

  void testnumbers() throws Throwable { doTest() }

  void testparenthed$exprInParenth() throws Throwable { doTest() }

  void testparenthed$paren1() throws Throwable { doTest() }

  void testparenthed$paren2() throws Throwable { doTest() }

  void testparenthed$paren3() throws Throwable { doTest() }

  void testparenthed$paren4() throws Throwable { doTest() }

  void testparenthed$paren5() throws Throwable { doTest() }

  void testparenthed$paren6() throws Throwable { doTest() }

  void testpath$method$ass4() throws Throwable { doTest() }

  void testpath$method$clazz1() throws Throwable { doTest() }

  void testpath$method$clazz2() throws Throwable { doTest() }

  void testpath$method$clos1() throws Throwable { doTest() }

  void testpath$method$clos2() throws Throwable { doTest() }

  void testpath$method$clos3() throws Throwable { doTest() }

  void testpath$method$clos4() throws Throwable { doTest() }

  void testpath$method$ind1() throws Throwable { doTest() }

  void testpath$method$ind2() throws Throwable { doTest() }

  void testpath$method$ind3() throws Throwable { doTest() }

  void testpath$method$method1() throws Throwable { doTest() }

  void testpath$method$method10() throws Throwable { doTest() }

  void testpath$method$method11() throws Throwable { doTest() }

  void testpath$method$method12() throws Throwable { doTest() }

  void testpath$method$method13() throws Throwable { doTest() }

  void testpath$method$method2() throws Throwable { doTest() }

  void testpath$method$method3() throws Throwable { doTest() }

  void testpath$method$method4() throws Throwable { doTest() }

  void testpath$method$method5() throws Throwable { doTest() }

  void testpath$method$method6() throws Throwable { doTest() }

  void testpath$method$method7() throws Throwable { doTest() }

  void testpath$method$method8() throws Throwable { doTest() }

  void testpath$method$method9() throws Throwable { doTest() }

  void testpath$path1() throws Throwable { doTest() }

  void testpath$path13() throws Throwable { doTest() }

  void testpath$path14() throws Throwable { doTest() }

  void testpath$path15() throws Throwable { doTest() }

  void testpath$path2() throws Throwable { doTest() }

  void testpath$path3() throws Throwable { doTest() }

  void testpath$path4() throws Throwable { doTest() }

  void testpath$path5() throws Throwable { doTest() }

  void testpath$path6() throws Throwable { doTest() }

  void testpath$path7() throws Throwable { doTest() }

  void testpath$path8() throws Throwable { doTest() }

  void testpath$path9() throws Throwable { doTest() }

  void testpath$path10() throws Throwable { doTest() }

  void testpath$regexp() { doTest() }

  void testpath$typeVsExpr() { doTest() }

  void testreferences$ref1() throws Throwable { doTest() }

  void testreferences$ref2() throws Throwable { doTest() }

  void testreferences$ref3() throws Throwable { doTest() }

  void testreferences$ref4() throws Throwable { doTest() }

  void testreferences$ref5() throws Throwable { doTest() }

  void testreferences$ref6() throws Throwable { doTest() }

  void testreferences$ref7() throws Throwable { doTest() }

  void testreferences$ref8() throws Throwable { doTest() }

  void testreferences$ref9() throws Throwable { doTest() }

  void testreferences$emptyTypeArgs() { doTest() }

  void testregex$chen() throws Throwable { doTest() }

  void testregex$GRVY1509err() throws Throwable { doTest("regex/GRVY-1509err.test") }

  void testregex$GRVY1509norm() throws Throwable { doTest("regex/GRVY-1509norm.test") }

  void testregex$GRVY1509test() throws Throwable { doTest("regex/GRVY-1509test.test") }

  void testregex$regex1() throws Throwable { doTest() }

  void testregex$regex10() throws Throwable { doTest() }

  void testregex$regex11() throws Throwable { doTest() }

  void testregex$regex12() throws Throwable { doTest() }

  void testregex$regex13() throws Throwable { doTest() }

  void testregex$regex14() throws Throwable { doTest() }

  void testregex$regex15() throws Throwable { doTest() }

  void testregex$regex16() throws Throwable { doTest() }

  void testregex$regex17() throws Throwable { doTest() }

  void testregex$regex18() throws Throwable { doTest() }

  void testregex$regex19() throws Throwable { doTest() }

  void testregex$regex2() throws Throwable { doTest() }

  void testregex$regex20() throws Throwable { doTest() }

  void testregex$regex21() throws Throwable { doTest() }

  void testregex$regex22() throws Throwable { doTest() }

  void testregex$regex23() throws Throwable { doTest() }

  void testregex$regex24() throws Throwable { doTest() }

  void testregex$regex25() throws Throwable { doTest() }

  void testregex$regex3() throws Throwable { doTest() }

  void testregex$regex33() throws Throwable { doTest() }

  void testregex$regex4() throws Throwable { doTest() }

  void testregex$regex5() throws Throwable { doTest() }

  void testregex$regex6() throws Throwable { doTest() }

  void testregex$regex7() throws Throwable { doTest() }

  void testregex$regex8() throws Throwable { doTest() }

  void testregex$regex9() throws Throwable { doTest() }

  void testregex$regex_begin() throws Throwable { doTest() }

  void testregex$regex_begin2() throws Throwable { doTest() }

  void testregex$multiLineSlashy() throws Throwable { doTest() }

  void testregex$dollarSlashy() throws Throwable { doTest() }

  void testregex$dollarSlashy2() throws Throwable { doTest() }

  void testregex$dollarSlashy3() throws Throwable { doTest() }

  void testregex$dollarSlashy4() throws Throwable { doTest() }

  void testregex$dollarSlashy5() throws Throwable { doTest() }

  void testregex$dollarSlashy6() throws Throwable { doTest() }

  void testregex$dollarSlashy7() throws Throwable { doTest() }

  void testregex$dollarSlashy8() throws Throwable { doTest() }

  void testregex$dollarSlashy9() throws Throwable { doTest() }

  void testregex$dollarSlashy10() { doTest() }

  void testregex$dollarSlashy11() { doTest() }

  void testregex$dollarSlashyCode() throws Throwable { doTest() }

  void testregex$dollarSlashyCodeUnfinished() throws Throwable { doTest() }

  void testregex$dollarSlashyEof() throws Throwable { doTest() }

  void testregex$dollarSlashyRegex() throws Throwable { doTest() }

  void testregex$dollarSlashyRegexFinishedTwice() throws Throwable { doTest() }

  void testregex$dollarSlashyRegexUnfinished() throws Throwable { doTest() }

  void testregex$dollarSlashyUnfinished() throws Throwable { doTest() }

  void testregex$dollarSlashyWindowsPaths() throws Throwable { doTest() }

  void testregex$dollarSlashyXml() throws Throwable { doTest() }

  void testregex$dollarSlashyDouble() throws Throwable { doTest() }

  void testregex$dollarSlashyTriple() throws Throwable { doTest() }

  void testregex$dollarSlashyUltimate() { doTest() }

  void testregex$afterNewLine() { doTest() }

  void testrelational$eq1() throws Throwable { doTest() }

  void testrelational$inst0() throws Throwable { doTest() }

  void testrelational$inst1() throws Throwable { doTest() }

  void testrelational$inst2() throws Throwable { doTest() }

  void testrelational$rel1() throws Throwable { doTest() }

  void testspecial$grvy1173() throws Throwable { doTest() }

  void testspecial$list1() throws Throwable { doTest() }

  void testspecial$list2() throws Throwable { doTest() }

  void testspecial$list3() throws Throwable { doTest() }

  void testspecial$map1() throws Throwable { doTest() }

  void testspecial$map2() throws Throwable { doTest() }

  void testspecial$map3() throws Throwable { doTest() }

  void testspecial$map4() throws Throwable { doTest() }

  void testspecial$map5() throws Throwable { doTest() }

  void testspecial$map6() throws Throwable { doTest() }

  void testspecial$map7() throws Throwable { doTest() }

  void testspecial$map8() throws Throwable { doTest() }

  void testspecial$paren13() throws Throwable { doTest() }

  void testtypecast$castToObject() throws Throwable { doTest() }

  void testtypecast$una1() throws Throwable { doTest() }

  void testtypecast$una2() throws Throwable { doTest() }

  void testtypecast$una3() throws Throwable { doTest() }

  void testtypecast$una4() throws Throwable { doTest() }

  void testtypecast$una5() throws Throwable { doTest() }

  void testtypecast$una6() throws Throwable { doTest() }

  void testtypecast$elvis() throws Throwable { doTest() }

  void testtypecast$conditional() throws Throwable { doTest() }

  void testAtHang() throws Throwable { doTest() }

  void testDollar() throws Throwable { doTest() }

  void testNoArrowClosure() throws Throwable { doTest() }

  void testNoArrowClosure2() throws Throwable { doTest() }

  void testPropertyAccessError() throws Throwable {
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

  void testthis$qualifiedThis() throws Throwable { doTest() }

  void testsuper$qualifiedSuper() throws Throwable { doTest() }

  void testthis$this() throws Throwable { doTest() }

  void testsuper$super() throws Throwable { doTest() }

  void testTripleEqual() throws Exception {
    checkParsingByText "2===3", """Groovy script
  Relational expression
    Literal
      PsiElement(Integer)('2')
    PsiElement(==)('===')
    Literal
      PsiElement(Integer)('3')
"""
  }

  void testcommandExpr$closureArg() { doTest() }

  void testcommandExpr$simple() { doTest() }

  void testcommandExpr$callArg1() { doTest() }

  void testcommandExpr$callArg2() { doTest() }

  void testcommandExpr$threeArgs1() { doTest() }

  void testcommandExpr$threeArgs2() { doTest() }

  void testcommandExpr$threeArgs3() { doTest() }

  void testcommandExpr$fourArgs() { doTest() }

  void testcommandExpr$fiveArgs() { doTest() }

  void testcommandExpr$multiArgs() { doTest() }

  void testcommandExpr$RHS() { doTest() }

  void testcommandExpr$oddArgCount() { doTest() }

  void testcommandExpr$indexAccess1() { doTest() }

  void testcommandExpr$indexAccess2() { doTest() }

  void testcommandExpr$indexAccess3() { doTest() }

  void testcommandExpr$indexAccess4() { doTest() }

  void testcommandExpr$closureArg2() { doTest() }

  void testcommandExpr$closureArg3() { doTest() }

  void testcommandExpr$not() { doTest() }

  void testcommandExpr$methodCall() { doTest() }

  void testcommandExpr$indexProperty() { doTest() }

  void testcommandExpr$instanceof() { doTest() }

  void testcommandExpr$instanceof2() { doTest() }

  void testcommandExpr$in() { doTest() }

  void testcommandExpr$as() { doTest() }

  void testcommandExpr$arrayAccess() { doTest() }

  void testcommandExpr$keywords() { doTest() }

  void testcommandExpr$literalInvoked() { doTest() }

  void testDiamond() { doTest() }

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

  void "test finish argument list on keyword occurrence"() {
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

  void testindexpropertyWithUnfinishedInvokedExpression() { doTest() }
}
