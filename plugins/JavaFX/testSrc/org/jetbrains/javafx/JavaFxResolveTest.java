package org.jetbrains.javafx;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.testUtils.JavaFxResolveTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxResolveTest extends JavaFxResolveTestCase {
  @Nullable
  @Override
  protected PsiElement doResolve() {
    final PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".fx");
    return ref.resolve();
  }

  public void testGlobalVar() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "x");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxFile.class);
  }

  public void testFunctionVar() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent().getParent(), JavaFxFunctionDefinition.class);
  }

  public void testLocalVar() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "x");
    assertInstanceOf(variableDeclaration.getParent().getParent(), JavaFxIfExpression.class);
  }

  public void testForParameter() {
    final JavaFxParameter parameter = assertResolvesTo(JavaFxParameter.class, "i");
    assertInstanceOf(parameter.getParent(), JavaFxInClause.class);
  }

  public void testFunctionParameter() {
    final JavaFxParameter parameter = assertResolvesTo(JavaFxParameter.class, "a");
    final PsiElement parent = parameter.getParent();
    assertInstanceOf(parent, JavaFxParameterList.class);
    assertInstanceOf(parent.getParent(), JavaFxSignature.class);
    assertInstanceOf(parent.getParent().getParent(), JavaFxFunctionDefinition.class);
  }

  public void testFunctionExpressionParameter() {
    final JavaFxParameter parameter = assertResolvesTo(JavaFxParameter.class, "a");
    final PsiElement parent = parameter.getParent();
    assertInstanceOf(parent, JavaFxParameterList.class);
    assertInstanceOf(parent.getParent(), JavaFxSignature.class);
    assertInstanceOf(parent.getParent().getParent(), JavaFxFunctionExpression.class);
  }

  public void testRecursiveFunction() {
    final JavaFxFunctionDefinition functionDefinition = assertResolvesTo(JavaFxFunctionDefinition.class, "foo");
    assertInstanceOf(functionDefinition.getParent(), JavaFxFile.class);
  }

  public void testThis() {
    assertResolvesTo(JavaFxClassDefinition.class, "A");
  }

  public void testObjectLiteralName() {
    assertResolvesTo(JavaFxClassDefinition.class, "Point");
  }

  public void testField() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "x");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testReturnType() {
    assertResolvesTo(JavaFxClassDefinition.class, "Point");
  }
  
  public void testCatchParameter() {
    final JavaFxParameter parameter = assertResolvesTo(JavaFxParameter.class, "e");
    final PsiElement parent = parameter.getParent();
    assertInstanceOf(parent, JavaFxCatchClause.class);
    assertNull(parent.getNextSibling());
  }

  public void testFromString() {
    assertResolvesTo(JavaFxVariableDeclaration.class, "a");
  }

  public void testVarFromOnReplace() {
    assertResolvesTo(JavaFxVariableDeclaration.class, "y");
  }

  public void testQuotedIdentifier() {
    assertResolvesTo(JavaFxVariableDeclaration.class, "<<var>>");
  }

  public void testQuotedIdentifier2() {
    assertResolvesTo(JavaFxClassDefinition.class, "<<C>>");
  }

  public void testQuotedIdentifier3() {
    final JavaFxFunctionDefinition functionDefinition = assertResolvesTo(JavaFxFunctionDefinition.class, "foo");
    assertInstanceOf(functionDefinition.getParent(), JavaFxClassDefinition.class);
  }

  public void testObjectLiteralVarFromInit() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxObjectLiteral.class);
  }

  public void testFieldFromObjectLiteralInit() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testFieldFromObjectLiteralFunction() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testFunctionReturnField() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testFunctionExpressionReturnField() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testSequenceElementMethod() {
    final JavaFxFunctionDefinition functionDefinition = assertResolvesTo(JavaFxFunctionDefinition.class, "foo");
    assertInstanceOf(functionDefinition.getParent(), JavaFxClassDefinition.class);
  }

  public void testVariableBeforeDefinition() {
    assertUnresolved();
  }

  public void testUndefinedVariable() {
    assertUnresolved();
  }

  public void testUndefinedVariable2() {
    assertUnresolved();
  }

  public void testIDEA_57804() {
    assertResolvesTo(JavaFxClassDefinition.class, "Test");
  }

  public void testQualifiedThis() {
    assertResolvesTo(JavaFxClassDefinition.class, "Test");
  }

  public void testThisMethod() {
    final JavaFxFunctionDefinition functionDefinition = assertResolvesTo(JavaFxFunctionDefinition.class, "bar");
    assertInstanceOf(functionDefinition.getParent(), JavaFxObjectLiteral.class);
  }

  public void testSuperClassMember() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testTypeExpression() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testForParameterMember() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "a");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }

  public void testVariableFromInit() {
    final JavaFxVariableDeclaration variableDeclaration = assertResolvesTo(JavaFxVariableDeclaration.class, "text");
    assertInstanceOf(variableDeclaration.getParent(), JavaFxClassDefinition.class);
  }
}
