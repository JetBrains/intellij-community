package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DContainingClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicToolWindowWrapper;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.tree.DMethodNode;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.tree.DPClassNode;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.02.2008
 */
public class GrDynamicImplicitMethodImpl extends GrDynamicImplicitElement {
  public GrDynamicImplicitMethodImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, GrReferenceExpression referenceExpression) {
    super(manager, nameIdentifier, type, writable, referenceExpression);
  }

  public GrDynamicImplicitMethodImpl(PsiManager manager, @NonNls String name, @NonNls String type, GrReferenceExpression referenceExpression) {
    super(manager, name, type, referenceExpression);
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
        if (!(myScope instanceof GrReferenceExpression)) return;

        final GrReferenceExpression refExpression = (GrReferenceExpression) myScope;
        final PsiClass expression = QuickfixUtil.findTargetClass(refExpression);

        final String className = expression.getQualifiedName();

        final Module module = DynamicToolWindowWrapper.getModule(myProject);
        if (module == null) return;

        DefaultMutableTreeNode desiredNode = null;
        final PsiElement method = myScope.getParent();

        final PsiType[] argumentTypes = QuickfixUtil.getMethodArgumentsTypes(((GrCallExpression) method));
        final String[] argumentNames = QuickfixUtil.getMethodArgumentsNames(((GrCallExpression) method));
        final List<Pair<String, PsiType>> pairs = QuickfixUtil.swapArgumentsAndTypes(argumentNames, argumentTypes);

        final PsiClassType fqClassName = PsiManager.getInstance(myProject).getElementFactory().createTypeByFQClassName(className, myProject.getAllScope());
        final PsiClass psiClass = fqClassName.resolve();
        if (psiClass == null) return;

        final Set<PsiClass> classes = GroovyUtils.findAllSupers(psiClass);
        classes.add(psiClass);
        for (PsiClass aClass : classes) {
          final String returnType = DynamicManager.getInstance(myProject).getMethodReturnType(module.getName(), aClass.getQualifiedName(), ((GrReferenceExpression) myScope).getName(), argumentTypes);
          if (returnType == null) continue;

          final DefaultMutableTreeNode classNode = TreeUtil.findNodeWithObject(treeRoot, new DPClassNode(new DContainingClassElement(aClass.getQualifiedName())));
          if (classNode == null) return;

          desiredNode = TreeUtil.findNodeWithObject(classNode, new DMethodNode(new DMethodElement(
              new DynamicVirtualMethod(refExpression.getName(), aClass.getQualifiedName(), module.getName(), returnType, pairs))));
        }

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

  @NotNull
  public SearchScope getUseScope() {
    return myScope.getProject().getAllScope();
  }


  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.PROPERTY;
  }
}