// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer.stubs;

import com.intellij.dev.psiViewer.PsiViewerDialog;
import com.intellij.dev.psiViewer.ViewerPsiBasedTree;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static com.intellij.dev.psiViewer.PsiViewerDialog.initTree;

public class StubViewerPsiBasedTree implements ViewerPsiBasedTree {

  public static final Logger LOG = Logger.getInstance(PsiViewerDialog.class);

  private @Nullable AbstractTreeModel myTreeModel;
  private final @NotNull Tree myStubTree;
  private final @NotNull StubDetailsViewer myStubDetailsViewer;
  private @Nullable JPanel myPanel;
  private final @NotNull Project myProject;
  private final @NotNull PsiTreeUpdater myUpdater;

  private volatile @NotNull Map<ASTNode, StubElement<?>> myNodeToStubs = new BidirectionalMap<>();
  Disposable myTreeModelDisposable = Disposer.newDisposable();


  public StubViewerPsiBasedTree(@NotNull Project project, @NotNull PsiTreeUpdater updater) {
    myProject = project;
    myUpdater = updater;
    myStubTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myStubDetailsViewer = new StubDetailsViewer(this);
  }

  @Override
  public void reloadTree(@Nullable PsiElement rootRootElement, @NotNull String text) {
    resetStubTree();
    buildStubTree(rootRootElement, text);
  }

  private void resetStubTree() {
    myStubTree.removeAll();
    if (myTreeModel != null) {
      Disposer.dispose(myTreeModelDisposable);
      myTreeModel = null;
      myTreeModelDisposable = Disposer.newDisposable();
    }

    myNodeToStubs = new BidirectionalMap<>();

    ViewerPsiBasedTree.removeListenerOfClass(myStubTree, StubTreeSelectionListener.class);
  }


  @Override
  public @NotNull JComponent getComponent() {
    if (myPanel != null) return myPanel;

    JBSplitter splitter = new JBSplitter("StubViewer.showPreviewDetails.proportion", 0.7f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myStubTree, true));
    AnAction action = myStubDetailsViewer.addComponent(splitter);

    ActionToolbarImpl toolbar = new ActionToolbarImpl("Stub Viewer", new DefaultActionGroup(action), false);
    toolbar.setTargetComponent(splitter);

    BorderLayoutPanel panel = new BorderLayoutPanel();
    panel.addToCenter(splitter).addToRight(toolbar);

    initTree(myStubTree);
    myPanel = panel;
    return panel;
  }

  @Override
  public boolean isFocusOwner() {
    return myStubTree.isFocusOwner();
  }

  @Override
  public void focusTree() {
    IdeFocusManager.getInstance(myProject).requestFocus(myStubTree, true);
  }

  private synchronized void buildStubTree(@Nullable PsiElement rootElement, @NotNull String textToParse) {
    if (rootElement == null) {
      myStubTree.setRootVisible(false);
      return;
    }
    if (!(rootElement instanceof PsiFileWithStubSupport)) {
      myStubTree.setRootVisible(false);
      StatusText text = myStubTree.getEmptyText();
      if (rootElement instanceof PsiFile) {
        text.setText("No stubs for " + rootElement.getLanguage().getDisplayName());
      }
      else {
        text.setText("Cannot build stub tree for code fragments");
      }
      return;
    }
    Stub stub = buildStubForElement(myProject, rootElement, textToParse);

    if (stub instanceof PsiFileStub) {
      PsiFileWithStubSupport file = (PsiFileWithStubSupport)rootElement;
      final StubTreeNode rootNode = new StubTreeNode((StubElement<?>)stub, null);
      StructureTreeModel<?> treeModel = new StructureTreeModel<>(new StubTreeStructure(rootNode), myTreeModelDisposable);
      myTreeModel = new AsyncTreeModel(treeModel, myTreeModelDisposable);
      myStubTree.setModel(myTreeModel);
      fillPsiToStubCache(file, (PsiFileStub<?>)stub);
      myStubTree.setRootVisible(true);
      myStubTree.expandRow(0);

      myStubTree.addTreeSelectionListener(new StubTreeSelectionListener());
      treeModel.invalidateAsync();
    }
    else {
      myStubTree.setRootVisible(false);
      StatusText text = myStubTree.getEmptyText();
      text.setText("Cannot build stubs for " + rootElement.getLanguage().getDisplayName());
    }
  }

  @Override
  public void dispose() {
    resetStubTree();
  }

  private static @Nullable Stub buildStubForElement(Project project, PsiElement rootElement, @NotNull String textToParse) {
    Stub stub = null;
    PsiFileWithStubSupport psiFile = (PsiFileWithStubSupport)rootElement;
    StubTree tree = psiFile.getStubTree();
    if (tree != null) {
      stub = tree.getRoot();
    }
    else if (rootElement instanceof PsiFileImpl) {
      StubBuilder stubBuilder = getStubBuilder((PsiFileImpl)rootElement);
      stub = stubBuilder == null ? null : stubBuilder.buildStubTree((PsiFile)rootElement);
    }
    if (stub == null) {
      LightVirtualFile file = new LightVirtualFile("stub", rootElement.getLanguage(), textToParse);
      final FileContent fc;
      try {
        fc = FileContentImpl.createByFile(file, project);
        fc.putUserData(IndexingDataKeys.PSI_FILE, psiFile);
        stub = StubTreeBuilder.buildStubTree(fc);
      }
      catch (IOException e) {
        LOG.warn(e.getMessage(), e);
      }
    }
    return stub;
  }

  private static @Nullable StubBuilder getStubBuilder(@NotNull PsiFileImpl rootElement) {
    IStubFileElementType<?> builder = rootElement.getElementTypeForStubBuilder();
    return builder == null ? null : builder.getBuilder();
  }

  @Override
  public void selectNodeFromPsi(@Nullable PsiElement element) {
    if (myTreeModel == null || element == null) return;
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof PsiFileWithStubSupport)) return;

    final DefaultMutableTreeNode rootNode = getRoot();
    if (rootNode == null) return;

    StubElement<?> stubElement = myNodeToStubs.get(element.getNode());
    if (stubElement != null) {
      selectStubElement(stubElement);
    }
    else {
      myStubTree.clearSelection();
    }
  }

  private void selectStubElement(StubElement<?> stubElement) {
    TreeNode node = TreeUtil.treeNodeTraverser(getRoot()).traverse().find(
      (treeNode) -> treeNode instanceof DefaultMutableTreeNode &&
                    ((StubTreeNode)((DefaultMutableTreeNode)treeNode).getUserObject()).getStub() == stubElement
    );
    if (node != null) {
      TreePath path = TreePathUtil.pathToTreeNode(node);
      myStubTree.getSelectionModel().setSelectionPath(path);
    }
  }

  private class StubTreeSelectionListener implements TreeSelectionListener {

    StubTreeSelectionListener() {
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      if (myTreeModel == null) return;

      final StubTreeNode rootNode = (StubTreeNode)getRoot().getUserObject();
      StubElement<?> topLevelStub = rootNode == null ? null : rootNode.getStub();
      if (!(topLevelStub instanceof PsiFileStub)) return;

      StubElement<?> stub = getSelectedStub();
      if (stub == null) return;
      PsiElement result = getPsiElementForStub(stub);


      if (result != null) {
        myUpdater.updatePsiTree(result, myStubTree.hasFocus() ? result.getTextRange() : null);
        myStubDetailsViewer.valueChanged(stub);
      }
    }
  }

  @Nullable StubElement<?> getSelectedStub() {
    TreePath selectionPath = myStubTree.getSelectionPath();
    return selectionPath != null
           ? ((StubTreeNode)((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject()).getStub()
           : null;
  }

  private DefaultMutableTreeNode getRoot() {
    return (DefaultMutableTreeNode)myStubTree.getModel().getRoot();
  }

  public PsiElement getPsiElementForStub(StubElement<?> stub) {
    Ref<PsiElement> result = Ref.create();
    myNodeToStubs.forEach((key, value) -> {
      if (value == stub) {
        result.set(key.getPsi());
      }
    });

    return result.get();
  }

  private void fillPsiToStubCache(@NotNull PsiFileWithStubSupport rootElement, @NotNull PsiFileStub<?> rootStub) {
    fillTreeForStub(rootElement, new StubTree(rootStub));
  }


  public void fillTreeForStub(@NotNull PsiFileWithStubSupport file, @NotNull StubTree tree) {
    StubBuilder builder = file instanceof PsiFileImpl ? getStubBuilder(((PsiFileImpl)file)) : null;
    if (builder == null) return;

    final Iterator<StubElement<?>> stubs = tree.getPlainList().iterator();
    final StubElement<?> root = stubs.next();
    final ASTNode ast = file.getNode();
    myNodeToStubs.put(ast, root);

    findTreeForStub(builder, ast, stubs);

    if (stubs.hasNext()) {
      LOG.error("Stub mismatch, unprocessed stubs " + stubs.next());
    }
  }

  private void findTreeForStub(StubBuilder builder, ASTNode tree, final Iterator<StubElement<?>> stubs) {
    final IElementType type = tree.getElementType();

    if (type instanceof IStubElementType && ((IStubElementType<?, ?>)type).shouldCreateStub(tree)) {
      if (!stubs.hasNext()) {
        LOG.error("Stub mismatch, " + type);
      }
      final StubElement<?> curStub = stubs.next();
      myNodeToStubs.put(tree, curStub);
    }


    for (ASTNode node : tree.getChildren(null)) {
      if (!builder.skipChildProcessingWhenBuildingStubs(tree, node)) {
        findTreeForStub(builder, node, stubs);
      }
    }
  }
}
