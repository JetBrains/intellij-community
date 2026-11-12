// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.conversions.ConvertMapToClassIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class ConvertMapToClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, null, TestUtils.getAbsoluteTestDataPath() + "intentions/convertMapToClass/");

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testVariableTypeReplace() {
    doTest();
  }

  @Test
  public void testBadCase() {
    doTest(false);
  }

  @Test
  public void testChangeReturnType() {
    doTest();
  }

  @Test
  public void testChangeMethodParameter() {
    doTest();
  }

  public void _testNotChangeReturnType() {
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean exists) {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(true) + "/Test.groovy");
    String hint = GroovyIntentionsBundle.message("convert.map.to.class.intention.name");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (!exists) {
      assertThat(list).isNullOrEmpty();
      return;
    }
    assertThat(list).hasSize(1);
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final GrListOrMap map = PsiTreeUtil.getParentOfType(element, GrListOrMap.class);
      assertNotNull(map);
      final GrTypeDefinition foo = ConvertMapToClassIntention.createClass(myFixture.getProject(), map.getNamedArguments(), "", "Foo");
      myFixture.addFileToProject(myFixture.getTestName(true) + "/Foo.groovy", foo.getContainingFile().getText());
      final PsiClass psiClass = myFixture.findClass("Foo");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> ConvertMapToClassIntention
        .replaceMapWithClass(myFixture.getProject(), map, psiClass, ConvertMapToClassIntention.checkForReturnFromMethod(map),
                             ConvertMapToClassIntention.checkForVariableDeclaration(map),
                             ConvertMapToClassIntention.checkForMethodParameter(map)));

      myFixture.checkResultByFile(myFixture.getTestName(true) + "/Foo.groovy", myFixture.getTestName(true) + "/Expected.groovy", true);
      myFixture.checkResultByFile(myFixture.getTestName(true) + "/Test_after.groovy", true);
    });
  }
}
