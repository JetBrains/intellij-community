package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.02.2008
 */
public class GrDynamicImplicitMethodImpl extends GrDynamicImplicitElement implements ItemPresentation, NavigationItem {
  private final List<MyPair> myPairs;

  public GrDynamicImplicitMethodImpl(PsiManager manager, @NonNls String name, @NonNls String type, String containingClassName, List<MyPair> pairs, PsiFile containingFile) {
    super(manager, name, type, containingClassName, containingFile);
    myPairs = pairs;
  }

  public void navigate(boolean requestFocus) {
    final Project myProject = myNameIdentifier.getProject();
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowWrapper.DYNAMIC_TOOLWINDOW_ID);

    window.activate(new Runnable() {
      public void run() {
        final TreeTable treeTable = DynamicToolWindowWrapper.getTreeTable(window, myProject);
        final ListTreeTableModelOnColumns model = DynamicToolWindowWrapper.getTreeTableModel(window, myProject);

        Object root = model.getRoot();

        if (root == null || !(root instanceof DefaultMutableTreeNode)) return;

        DefaultMutableTreeNode treeRoot = ((DefaultMutableTreeNode) root);
        DefaultMutableTreeNode desiredNode;

        final PsiClassType fqClassName = myManager.getElementFactory().createTypeByFQClassName(getContainingClassName(), myProject.getAllScope());
        final PsiClass psiClass = fqClassName.resolve();
        if (psiClass == null) return;

        PsiClass trueClass = null;
        DMethodElement methodElement = null;

        for (PsiClass aSuper : PsiUtil.iterateSupers(psiClass, true)) {
          methodElement = DynamicManager.getInstance(myProject).findConcreteDynamicMethod(aSuper.getQualifiedName(), getName(), QuickfixUtil.getArgumentsTypes(myPairs));

          if (methodElement != null) {
            trueClass = aSuper;
            break;
          }
        }

        if (trueClass == null) return;
        final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DClassElement(myProject, trueClass.getQualifiedName(), false));

        if (classNode == null) return;
        desiredNode = TreeUtil.findNodeWithObject(classNode, methodElement);

        if (desiredNode == null) return;
        final TreePath path = TreeUtil.getPathFromRoot(desiredNode);

        treeTable.getTree().expandPath(path);
        treeTable.getTree().setSelectionPath(path);
        treeTable.getTree().fireTreeExpanded(path);

        treeTable.requestFocus();
        treeTable.revalidate();
        treeTable.repaint();
      }
    }, true);
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isPhysical() {
    return true;
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.METHOD;
  }

  public List<MyPair> getPairs() {
    return myPairs;
  }

  public boolean isValid() {
    final GrDynamicImplicitMethodImpl method = DynamicManager.getInstance(myManager.getProject()).getMethod(
        myManager,
        getName(),
        getType().getCanonicalText(),
        getContainingClassName(),
        getPairs(),
        getContainingFile());

    return method == this;
  }
}