// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class ChangeSignatureTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.JAVA_LATEST, "refactoring/changeSignature/");
  private final TestFixture<String> testName = testNameFixture(false);

  @Test
  public void testOneNewParameter() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("p", -1, "\"5\"", null, CommonClassNames.JAVA_LANG_STRING));
    });
  }

  @Test
  public void testRemoveParameterMultiline() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0), new SimpleInfo(2));
    });
  }

  @Test
  public void testMoveParametersMultiline() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(1), new SimpleInfo(0), new SimpleInfo(2));
    });
  }

  @Test
  public void testRemoveParameter() {
    runInEdtAndWait(() -> {
      doTest();
    });
  }

  @Test
  public void testInsertParameter() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0), new SimpleInfo("p", -1, "5", "-3", PsiTypes.intType()), new SimpleInfo(1));
    });
  }

  @Test
  public void testInsertOptionalParameter() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0), new SimpleInfo(1), new SimpleInfo("p", -1, "5", "-3", PsiTypes.intType()));
    });
  }

  @Test
  public void testNamedParametersRemove() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(1), new SimpleInfo(2));
    });
  }

  @Test
  public void testNamedParametersOrder1() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0),
             new SimpleInfo(2));
    });
  }

  /*public void testNamedParametersOrder2() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", null, com.intellij.psi.PsiTypes.intType(),
      new SimpleInfo(2),
                                 });
  }

  @Test
  public void testNamedParametersOrder3() throws Exception {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo[]{
        new SimpleInfo(0),
        new SimpleInfo(2),
        new SimpleInfo("p", -1, "5", null, com.intellij.psi.PsiTypes.intType(),
                                   });
    });
  }*/

  @Test
  public void testMoveNamedParameters() throws Exception {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(1),
             new SimpleInfo(0));
    });
  }

  @Test
  public void testMoveVarArgParameters() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(1), new SimpleInfo(0));
    });
  }

  @Test
  public void testChangeVisibilityAndName() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PROTECTED, "newName", null, new ArrayList<>(Arrays.asList(new SimpleInfo(0))),
             new ArrayList<>(), false);
    });
  }

  @Test
  public void testImplicitConstructorInConstructor() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("p", -1, "5", null, PsiTypes.intType()));
    });
  }

  @Test
  public void testImplicitConstructorForClass() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("p", -1, "5", null, PsiTypes.intType()));
    });
  }

  @Test
  public void testAnonymousClassUsage() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("p", -1, "5", null, PsiTypes.intType()));
    });
  }

  @Test
  public void testGroovyDocReferences() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0), new SimpleInfo(2));
    });
  }

  @Test
  public void testOverriders() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, "bar", null, new ArrayList<>(Arrays.asList(new SimpleInfo(0))),
             new ArrayList<>(), false);
    });
  }

  @Test
  public void testParameterRename() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("newP", 0));
    });
  }

  @Test
  public void testAddReturnType() {
    runInEdtAndWait(() -> {
      doTest("int", new SimpleInfo(0));
    });
  }

  @Test
  public void testChangeReturnType() {
    runInEdtAndWait(() -> {
      doTest("int", new SimpleInfo(0));
    });
  }

  @Test
  public void testRemoveReturnType() {
    runInEdtAndWait(() -> {
      doTest("", new SimpleInfo(0));
    });
  }

  @Test
  public void testChangeParameterType() {
    runInEdtAndWait(() -> {
      doTest("", new SimpleInfo("p", 0, null, null, PsiTypes.intType()));
    });
  }

  @Test
  public void testGenerateDelegate() {
    runInEdtAndWait(() -> {
      doTest("", true, new SimpleInfo(0), new SimpleInfo("p", -1, "2", "2", PsiTypes.intType()));
    });
  }

  @Test
  public void testAddException() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, null, "",
             Arrays.asList(new SimpleInfo(0)),
             Arrays.asList(new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))),
             false);
    });
  }

  @Test
  public void testExceptionCaughtInUsage() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, null, "",
             Arrays.asList(new SimpleInfo(0)),
             Arrays.asList(new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))),
             false);
    });
  }

  @Test
  public void testExceptionInClosableBlock() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, null, "",
             Arrays.asList(new SimpleInfo(0)),
             Arrays.asList(new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))),
             false);
    });
  }

  @Test
  public void testGenerateDelegateForConstructor() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, "Foo", null,
             new ArrayList<>(Arrays.asList(new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiTypes.intType()))),
             new ArrayList<>(), true);
    });
  }

  @Test
  public void testGenerateDelegateForAbstract() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, "foo", null,
             new ArrayList<>(Arrays.asList(new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiTypes.intType()))),
             new ArrayList<>(), true);
    });
  }

  @Test
  public void testTypeParameters() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("list", -1, "null", null, "java.util.List<T>"), new SimpleInfo(0));
    });
  }

  @Test
  public void testEnumConstructor() throws Exception {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("a", -1, "2", null, PsiTypes.intType()));
    });
  }

  @Test
  public void testMoveArrayToTheEnd() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(1), new SimpleInfo(0));
    });
  }

  @Test
  public void testReplaceVarargWithArray() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("l", 1, null, null, "List<T>[]"), new SimpleInfo(0));
    });
  }

  @Test
  public void testReplaceVarargWithArray2() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("l", 1, null, null, "Map<T, E>[]"), new SimpleInfo(0));
    });
  }

  @Test
  public void testConstructorCall() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0), new SimpleInfo("a", -1, "1", null, PsiTypes.intType()));
    });
  }

  @Test
  public void testNoArgInCommandCall() {
    runInEdtAndWait(() -> {
      doTest();
    });
  }

  @Test
  public void testClosureArgs() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0));
    });
  }

  @Test
  public void testRemoveSingleClosureArgument() {
    runInEdtAndWait(() -> {
      doTest();
    });
  }

  @Test
  public void testNewExpr() {
    runInEdtAndWait(() -> {
      doTest();
    });
  }

  @Test
  public void testChangeJavaDoc() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("newName", 0), new SimpleInfo(1));
    });
  }

  @Test
  public void testDefaultInitializerInJava() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("p", -1, "", "1", ""));
    });
  }

  @Test
  public void testChangeType() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, "foo", "List<String>", new ArrayList<>(), new ArrayList<>(), false);
    });
  }

  @Test
  public void testDifferentParamNameInOverriden() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("newName", 0));
    });
  }

  @Test
  public void testFeelLucky() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo("lucky", -1, "defValue", "defInit", "java.lang.String", true));
    });
  }

  @Test
  public void testParamsWithGenerics() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(0));
    });
  }

  @Test
  public void testGenerateDelegateWithOtherName() {
    runInEdtAndWait(() -> {
      doTest(PsiModifier.PUBLIC, "doSmthElse", null, new ArrayList<>(), new ArrayList<>(), true);
    });
  }

  @Test
  public void testFailBecauseOfOptionalParam() {
    runInEdtAndWait(() -> {
      try {
        doTest(new SimpleInfo("optional", -1, null, "1", "int"));
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        Assertions.assertEquals("Method <b><code>foo(int)</code></b> is already defined in class <b><code>Test</code></b>", e.getMessage());
        return;
      }

      Assertions.fail("conflicts are not detected!");
    });
  }

  @Test
  public void testRenameToLiteral() {
    runInEdtAndWait(() -> {
      doTest(null, "a bc", null, new ArrayList<>(), new ArrayList<>(), false);
    });
  }

  @Test
  public void testRenameToLiteral2() {
    runInEdtAndWait(() -> {
      doTest(null, "a'bc", null, new ArrayList<>(), new ArrayList<>(), false);
    });
  }

  @Test
  public void testLineFeedInCommandArgs() {
    runInEdtAndWait(() -> {
      doTest(new SimpleInfo(1));
    });
  }

  @Test
  public void testTraitMethod() {
    runInEdtAndWait(() -> {
      doTest(null, null, null, new ArrayList<>(Arrays.asList(new SimpleInfo("a", -1))), new ArrayList<>(),
             false);
    });
  }

  private PsiType createType(String typeText) {
    return JavaPsiFacade.getElementFactory(groovy.get().getProject()).createTypeByFQClassName(typeText, GlobalSearchScope.allScope(groovy.get().getProject()));
  }

  private void doTest(String newReturnType, boolean generateDelegate, SimpleInfo... parameterInfos) {
    doTest(PsiModifier.PUBLIC, null, newReturnType, DefaultGroovyMethods.asType(parameterInfos, List.class),
           new ArrayList<>(), generateDelegate);
  }

  private void doTest(String newReturnType, SimpleInfo... parameterInfos) {
    doTest(newReturnType, false, parameterInfos);
  }

  private void doTest(SimpleInfo... parameterInfos) {
    doTest(null, false, parameterInfos);
  }

  private void doTest(@Nullable @PsiModifier.ModifierConstant String newVisibility,
                      @Nullable String newName,
                      @Nullable String newReturnType,
                      @NotNull List<SimpleInfo> parameterInfo,
                      @NotNull List<ThrownExceptionInfo> exceptionInfo,
                      final boolean generateDelegate) {
    final String javaTestName = testName.get() + ".java";
    final String groovyTestName = testName.get() + ".groovy";


    final File javaSrc = new File(groovy.get().getTestDataPath() + "/" + javaTestName);
    if (javaSrc.exists()) {
      groovy.get().copyFileToProject(javaTestName);
    }


    groovy.get().configureByFile(groovyTestName);
    executeRefactoring(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo, groovy.get().getProject()),
                       new SimpleExceptionsGen(exceptionInfo), generateDelegate);
    if (javaSrc.exists()) {
      groovy.get().checkResultByFile(javaTestName, testName.get() + "_after.java", true);
    }

    groovy.get().checkResultByFile(groovyTestName, testName.get() + "_after.groovy", true);
  }

  private void executeRefactoring(@Nullable @PsiModifier.ModifierConstant String newVisibility,
                                  @Nullable String newName,
                                  @Nullable String newReturnType,
                                  @NotNull SimpleParameterGen genParams,
                                  @NotNull SimpleExceptionsGen genExceptions,
                                  boolean generateDelegate) {
    final PsiElement targetElement = new GrChangeSignatureHandler().findTargetMember(groovy.get().getFile(), groovy.get().getEditor());
    assertTrue(targetElement instanceof GrMethod, "<caret> is not on method name");
    GrMethod method = (GrMethod)targetElement;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(groovy.get().getProject()).getElementFactory();
    PsiType newType;
    if (newReturnType == null) {
      if (method.getReturnTypeElement() == null) {
        newType = null;
      }
      else {
        newType = method.getReturnType();
      }
    }
    else if (newReturnType.length() == 0) {
      newType = null;
    }
    else {
      newType = factory.createTypeFromText(newReturnType, method);
    }
    GrChangeInfoImpl changeInfo =
      new GrChangeInfoImpl(method,
                           newVisibility,
                           newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
                           newName != null ? newName : method.getName(),
                           Arrays.asList(genParams.genParams(method)),
                           genExceptions.genExceptions(method),
                           generateDelegate);
    new GrChangeSignatureProcessor(groovy.get().getProject(), changeInfo).run();
  }

}
