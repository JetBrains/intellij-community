package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import junit.framework.Assert;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class StubsTest extends LightCodeInsightFixtureTestCase {
  
  public void testConfig_object() { doTest(); }
  public void testSlurper() { doTest(); }
  public void testStub1() { doTest(); }
  public void testStub_field1() { doTest(); }
  public void testStub_method1() { doTest(); }
  public void testTypeParameters() { doTest(); }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/stubs";
  }

  public void doTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    String fileText = data.get(0);

    PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(getProject(), fileText);

    ASTNode node = psiFile.getNode();
    Assert.assertNotNull(node);
    IElementType type = node.getElementType();
    Assert.assertTrue(type instanceof IStubFileElementType);

    IStubFileElementType stubFileType = (IStubFileElementType) type;
    StubBuilder builder = stubFileType.getBuilder();
    StubElement element = builder.buildStubTree(psiFile);
    StringBuffer buffer = new StringBuffer();
    getStubsTreeImpl(element, buffer, "");
    String stubTree = buffer.toString().trim();
    assertEquals(data.get(1), stubTree);
  }

  private static void getStubsTreeImpl(StubElement element, StringBuffer buffer, String offset) {
    PsiElement psi = element.getPsi();
    buffer.append(offset).append("[").append(psi.toString()).
            append(element instanceof NamedStub ? " : " + ((NamedStub) element).getName() : "").
            append("]\n");
    for (StubElement stubElement : ((List<StubElement>) element.getChildrenStubs())) {
      getStubsTreeImpl(stubElement, buffer, offset + "  ");
    }
  }

}
