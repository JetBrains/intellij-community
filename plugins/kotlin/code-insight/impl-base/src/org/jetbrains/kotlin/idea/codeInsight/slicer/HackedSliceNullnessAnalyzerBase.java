// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.slicer;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.*;
import com.intellij.util.WalkingState;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle;

import java.util.*;

public abstract class HackedSliceNullnessAnalyzerBase {
  private final @NotNull SliceLeafEquality myLeafEquality;

  private final @NotNull SliceLanguageSupportProvider myProvider;

  public HackedSliceNullnessAnalyzerBase(@NotNull SliceLeafEquality leafEquality,
          @NotNull SliceLanguageSupportProvider provider) {
    myLeafEquality = leafEquality;
    myProvider = provider;
  }

  private void groupByNullness(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
    SliceRootNode root = createNewTree(result, oldRoot, map);

    SliceUsage rootUsage = oldRoot.getCachedChildren().get(0).getValue();
    SliceManager.getInstance(root.getProject())
        .createToolWindow(true, root,
                  true,
                  SliceManager.getElementDescription(
                          null,
                          rootUsage.getElement(),
                          KotlinCodeInsightBundle.message(
                          "slice.nullness.tab.title.grouped.by.nullness")));
  }

  public @NotNull SliceRootNode createNewTree(NullAnalysisResult result, SliceRootNode oldRoot, final Map<SliceNode, NullAnalysisResult> map) {
    SliceRootNode root = oldRoot.copy();
    assert oldRoot.getCachedChildren().size() == 1;
    SliceNode oldRootStart = oldRoot.getCachedChildren().get(0);
    root.setChanged();
    root.targetEqualUsages.clear();

    List<HackedSliceLeafValueClassNode> children = new ArrayList<>();
    ContainerUtil.addIfNotNull(children, createValueRootNode(result, oldRoot, map, root, oldRootStart, "Null Values", NullAnalysisResult.NULLS));
    ContainerUtil.addIfNotNull(children, createValueRootNode(result, oldRoot, map, root, oldRootStart, "NotNull Values", NullAnalysisResult.NOT_NULLS));
    ContainerUtil.addIfNotNull(children, createValueRootNode(result, oldRoot, map, root, oldRootStart, "Other Values", NullAnalysisResult.UNKNOWNS));
    root.setChildren(children);
    return root;
  }

  private HackedSliceLeafValueClassNode createValueRootNode(
          NullAnalysisResult result,
          SliceRootNode oldRoot,
          final Map<SliceNode, NullAnalysisResult> map,
          SliceRootNode root,
          SliceNode oldRootStart,
          String nodeName,
          final int group) {
    Collection<PsiElement> groupedByValue = result.groupedByValue[group];
    if (groupedByValue.isEmpty()) {
      return null;
    }
    HackedSliceLeafValueClassNode
            valueRoot = new HackedSliceLeafValueClassNode(root.getProject(), root, nodeName);

    Set<PsiElement> uniqueValues = CollectionFactory.createCustomHashingStrategySet(myLeafEquality);
      uniqueValues.addAll(groupedByValue);
    for (final PsiElement expression : uniqueValues) {
      SliceNode newRoot = SliceLeafAnalyzer.filterTree(oldRootStart, oldNode -> {
        if (oldNode.getDuplicate() != null) {
          return null;
        }

        for (PsiElement nullSuspect : group(oldNode, map, group)) {
          if (PsiEquivalenceUtil.areElementsEquivalent(nullSuspect, expression)) {
            return oldNode.copy();
          }
        }
        return null;
      }, (node, children) -> {
        if (!children.isEmpty()) return true;
        PsiElement element = node.getValue().getElement();
        if (element == null) return false;
        return PsiEquivalenceUtil.areElementsEquivalent(element, expression); // leaf can be there only if it's filtering expression
      });
      valueRoot.myCachedChildren.add(
              new SliceLeafValueRootNode(root.getProject(),
                                         valueRoot,
                                         myProvider.createRootUsage(expression, oldRoot.getValue().params),
                                         Collections.singletonList(newRoot))
      );
    }
    return valueRoot;
  }

  public void startAnalyzeNullness(@NotNull AbstractTreeStructure treeStructure, @NotNull Runnable finish) {
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    final Ref<NullAnalysisResult> leafExpressions = Ref.create(null);
    final Map<SliceNode, NullAnalysisResult> map = createMap();

      ProgressManager.getInstance().run(new Task.Backgroundable(
              root.getProject(),
              KotlinCodeInsightBundle.message("slice.nullness.progress.title.expanding.all.nodes"), true) {
      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        NullAnalysisResult l = calcNullableLeaves(root, treeStructure, map);
        leafExpressions.set(l);
      }

      @Override
      public void onCancel() {
        finish.run();
      }

      @Override
      public void onSuccess() {
        try {
          NullAnalysisResult leaves = leafExpressions.get();
          if (leaves == null) return;  //cancelled

          groupByNullness(leaves, root, map);
        }
        finally {
          finish.run();
        }
      }
    });
  }

  public static Map<SliceNode, NullAnalysisResult> createMap() {
    return FactoryMap.createMap(k->new NullAnalysisResult(), () -> new Reference2ObjectOpenHashMap<>());
  }

  private static NullAnalysisResult node(@NotNull SliceNode node, @NotNull Map<SliceNode, NullAnalysisResult> nulls) {
    return nulls.get(node);
  }
  private static Collection<PsiElement> group(@NotNull SliceNode node, @NotNull Map<SliceNode, NullAnalysisResult> nulls, int group) {
    return nulls.get(node).groupedByValue[group];
  }

  public @NotNull NullAnalysisResult calcNullableLeaves(
          final @NotNull SliceNode root,
          @NotNull AbstractTreeStructure treeStructure,
          final @NotNull Map<SliceNode, NullAnalysisResult> map) {
    final SliceLeafAnalyzer.SliceNodeGuide guide = new SliceLeafAnalyzer.SliceNodeGuide(treeStructure);
    WalkingState<SliceNode> walkingState = new WalkingState<>(guide) {
        @Override
        public void visit(final @NotNull SliceNode element) {
            element.calculateDupNode();
            node(element, map).clear();
            SliceNode duplicate = element.getDuplicate();
            if (duplicate != null) {
                node(element, map).add(node(duplicate, map));
            } else {
                final PsiElement value = ReadAction.compute(() -> element.getValue().getElement());
                boolean canBeLeaf = ReadAction.compute(() -> element.getValue().canBeLeaf());
                Nullability nullability = canBeLeaf ? ReadAction.compute(() -> checkNullability(value)) : Nullability.UNKNOWN;
                if (nullability == Nullability.NULLABLE) {
                    group(element, map, NullAnalysisResult.NULLS).add(value);
                } else if (nullability == Nullability.NOT_NULL) {
                    group(element, map, NullAnalysisResult.NOT_NULLS).add(value);
                } else {
                    Collection<? extends AbstractTreeNode> children = ReadAction.compute(element::getChildren);
                    if (children.isEmpty()) {
                        group(element, map, NullAnalysisResult.UNKNOWNS).add(value);
                    }
                    super.visit(element);
                }
            }
        }

        @Override
        public void elementFinished(@NotNull SliceNode element) {
            SliceNode parent = guide.getParent(element);
            if (parent != null) {
                node(parent, map).add(node(element, map));
            }
        }
    };
    walkingState.visit(root);

    return node(root, map);
  }

  /**
   * Implementors must override this method; default implementation just throws UnsupportedOperationException.
   *
   * @param element element to find nullability for
   * @return element nullability
   */
  protected @NotNull Nullability checkNullability(final PsiElement element) {
    throw new UnsupportedOperationException();
  }

  public static class NullAnalysisResult {
    static final int NULLS = 0;
    static final int NOT_NULLS = 1;
    static final int UNKNOWNS = 2;
    // it's important to use LinkedHashSet's to have consistent output in tests
    final Collection<PsiElement>[] groupedByValue =
            new Collection[] {new LinkedHashSet<PsiElement>(), new LinkedHashSet<PsiElement>(), new LinkedHashSet<PsiElement>()};

    public void clear() {
      for (Collection<PsiElement> elements : groupedByValue) {
        elements.clear();
      }
    }

    private void add(NullAnalysisResult duplicate) {
      for (int i = 0; i < groupedByValue.length; i++) {
        Collection<PsiElement> elements = groupedByValue[i];
        Collection<PsiElement> other = duplicate.groupedByValue[i];
        elements.addAll(other);
      }
    }
  }
}

