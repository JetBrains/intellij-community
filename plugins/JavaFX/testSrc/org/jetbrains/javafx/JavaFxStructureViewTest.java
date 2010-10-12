package org.jetbrains.javafx;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import junit.framework.Assert;
import org.jetbrains.javafx.testUtils.JavaFxLightFixtureTestCase;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxStructureViewTest extends JavaFxLightFixtureTestCase {
  private Object[] getTopLevelItems() {
    final StructureView structureView = createStructureViewModel();
    final StructureViewModel structureViewModel = structureView.getTreeModel();
    final AbstractTreeStructure treeStructure = new SmartTreeStructure(myFixture.getProject(), structureViewModel);
    final Object[] items = TreeStructureUtil.getChildElementsFromTreeStructure(treeStructure, treeStructure.getRootElement());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        structureViewModel.dispose();
        Disposer.dispose(structureView);
      }
    });
    return items;
  }

  private StructureView createStructureViewModel() {
    final PsiFile psiFile = myFixture.configureByFile("/structure/" + getTestName(false) + ".fx");
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    final FileType fileType = virtualFile.getFileType();
    final Project project = myFixture.getProject();
    final StructureViewBuilder structureViewBuilder = StructureViewBuilder.PROVIDER.getStructureViewBuilder(fileType, virtualFile, project);
    return structureViewBuilder.createStructureView(FileEditorManager.getInstance(project).getSelectedEditor(virtualFile), project);
  }

  private static String getText(final Object item) {
    final Object value = ((AbstractTreeNode)item).getValue();
    if (value instanceof TreeElement) {
      return ((TreeElement)value).getPresentation().getPresentableText();
    }
    if (value instanceof Group) {
      return ((Group)value).getPresentation().getPresentableText();
    }
    Assert.fail("Unexpected tree node type: " + item);
    return null;
  }

  public void testFunctions() {
    final Object[] topLevelItems = getTopLevelItems();
    assertEquals(topLevelItems.length, 3);
    assertEquals("bar(a: Integer, b: Integer)", getText(topLevelItems[0]));
    assertEquals("foo()", getText(topLevelItems[1]));
    assertEquals("run(args: String[])", getText(topLevelItems[2]));
  }

  public void testClasses() {
    final Object[] topLevelItems = getTopLevelItems();
    assertEquals(topLevelItems.length, 3);
    assertEquals("<<class>>>", getText(topLevelItems[0]));
    assertEquals("A", getText(topLevelItems[1]));
    assertEquals("B", getText(topLevelItems[2]));
  }

  public void testVars() {
    final Object[] topLevelItems = getTopLevelItems();
    assertEquals(topLevelItems.length, 3);
    assertEquals("a", getText(topLevelItems[0]));
    assertEquals("b", getText(topLevelItems[1]));
    assertEquals("c", getText(topLevelItems[2]));
  }

  public void testAll() {
    final Object[] topLevelItems = getTopLevelItems();
    assertEquals(topLevelItems.length, 3);
    assertEquals("A", getText(topLevelItems[0]));
    assertEquals("a", getText(topLevelItems[1]));
    assertEquals("foo()", getText(topLevelItems[2]));
  }
}
