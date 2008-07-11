package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.testcases.GroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.04.2008
 */
public class DynamicTest extends GroovyFileSetTestCase {
  private CodeInsightTestFixture myCodeInsightFixture;

  public DynamicTest() {
    super(TestUtils.getTestDataPath() + "/dynamic/");
  }

  public String getSearchPattern() {
    return "(.*)\\.groovy";
  }

  protected void runTest(final File file) throws Throwable {
    final Ref<Pair<String, DItemElement>> result = new Ref<Pair<String, DItemElement>>();

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {

            try {
              result.set(doDynamicFix(myProject, file.getName(), file));
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            } catch (Throwable throwable) {
              throwable.printStackTrace();
            }
          }
        });
      }
    }, null, null);

    final Pair<String, DItemElement> pair = result.get();

    final DRootElement rootElement = DynamicManager.getInstance(myProject).getRootElement();
    final DClassElement classElement = rootElement.getClassElement(pair.getFirst());
    assert classElement != null;

    final DItemElement itemElement = pair.getSecond();
    if (itemElement instanceof DMethodElement) {
      final String[] types = QuickfixUtil.getArgumentsTypes(((DMethodElement) itemElement).getPairs());
      assert classElement.getMethod(itemElement.getName(), types) != null;
    } else {
      assert classElement.getPropertyByName(itemElement.getName()) != null;
    }

//    return fileText;
  }

  private Pair<String, DItemElement> doDynamicFix(Project project, String relPath, File file) throws Throwable {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    assert virtualFile != null;

    final PsiFile myFile = PsiManager.getInstance(project).findFile(virtualFile);
    assert myFile != null;

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
    myCodeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myCodeInsightFixture.setTestDataPath(TestUtils.getTestDataPath());
    myCodeInsightFixture.setUp();

    final List<IntentionAction> actions = myCodeInsightFixture.getAvailableIntentions("/dynamic/" + relPath);

    DynamicFix dynamicFix = null;
    for (IntentionAction action : actions) {
      if (action instanceof DynamicFix) {
        dynamicFix = ((DynamicFix) action);
        break;
      }
    }

    if (dynamicFix == null) return null;
    dynamicFix.invoke(project);

    final GroovyFile groovyFile = (GroovyFile) myFile;
    final GrTypeDefinition[] grTypeDefinitions = groovyFile.getTypeDefinitions();
    final PsiClass classDefinition;
    if (!groovyFile.isScript()) {
      classDefinition = grTypeDefinitions[0];
    } else {
      classDefinition = groovyFile.getScriptClass();
    }
    assert classDefinition != null;

    DItemElement itemElement;
    final GrReferenceExpression referenceExpression = dynamicFix.getReferenceExpression();

    if (dynamicFix.isMethod()) {
      final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false, false);
      final String[] methodArgumentsNames = QuickfixUtil.getMethodArgumentsNames(project, psiTypes);
      final List<MyPair> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

      itemElement = new DMethodElement(false, referenceExpression.getName(), "java.lang.Object", pairs);
    } else {
      itemElement = new DPropertyElement(false, referenceExpression.getName(), "java.lang.Object");
    }

    return new Pair<String, DItemElement>(classDefinition.getQualifiedName(), itemElement);
  }

  protected void tearDown() {
    try {
      myCodeInsightFixture.tearDown();
      myCodeInsightFixture = null;
      myFixture.tearDown();
    } catch (Exception e) {
      e.printStackTrace();
    }
    super.tearDown();
  }

  public static Test suite() {
    return new DynamicTest();
  }
}
