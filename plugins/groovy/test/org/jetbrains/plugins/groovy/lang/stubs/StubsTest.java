package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class StubsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/stubs");
  }

  @Test
  public void testConfig_object() {
    doTest();
  }
  @Test
  public void testSlurper() {
    doTest();
  }
  @Test
  public void testStub1() {
    doTest();
  }
  @Test
  public void testStub_field1() {
    doTest();
  }
  @Test
  public void testStub_method1() {
    doTest();
  }
  @Test
  public void testTypeParameters() {
    doTest();
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + "/" + myFixture.getTestName(true) + ".test");
    String fileText = data.get(0);

    ReadAction.runBlocking(() -> {
      PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(myFixture.getProject(), fileText);

      ASTNode node = psiFile.getNode();
      assertNotNull(node);
      IElementType type = node.getElementType();
      assertTrue(type instanceof IStubFileElementType);

      IStubFileElementType stubFileType = (IStubFileElementType) type;
      StubBuilder builder = stubFileType.getBuilder();
      StubElement element = builder.buildStubTree(psiFile);
      StringBuffer buffer = new StringBuffer();
      getStubsTreeImpl(element, buffer, "");
      String stubTree = buffer.toString().trim();
      assertEquals(data.get(1), stubTree);
    });
  }

  private static void getStubsTreeImpl(StubElement element, StringBuffer buffer, String offset) {
    PsiElement psi = element.getPsi();
    buffer.append(offset).append("[").append(psi.toString()).
            append(element instanceof NamedStub ? " : " + ((NamedStub<?>) element).getName() : "").
            append("]\n");
    for (StubElement stubElement : ((List<StubElement>) element.getChildrenStubs())) {
      getStubsTreeImpl(stubElement, buffer, offset + "  ");
    }
  }

}
