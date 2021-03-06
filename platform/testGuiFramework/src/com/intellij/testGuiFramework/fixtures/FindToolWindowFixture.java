// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.impl.GroupNode;
import org.fest.swing.edt.GuiQuery;
import org.fest.util.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

public class FindToolWindowFixture {
  @NotNull private final IdeFrameFixture myParent;

  public FindToolWindowFixture(@NotNull IdeFrameFixture parent) {
    myParent = parent;
  }

  @NotNull
  public ContentFixture getSelectedContext() {
    return new ContentFixture(myParent);
  }

  public static class ContentFixture {
    @NotNull private final Content myContent;

    ContentFixture(@NotNull IdeFrameFixture parent) {
      UsageViewContentManager usageViewManager = UsageViewContentManager.getInstance(parent.getProject());
      myContent = usageViewManager.getSelectedContent();
      assertNotNull(myContent);
    }


    public void findUsagesInGeneratedCodeGroup() {
      findUsageGroup("Usages in generated code");
    }

    public void findUsageGroup(@NotNull final String groupText) {
      final Tree tree = getContentsTree();
      final List<String> groupNames = Lists.newArrayList();
      GroupNode foundGroup = execute(new GuiQuery<>() {
        @Override
        @Nullable
        protected GroupNode executeInEDT() throws Throwable {
          GroupNode rootNode = (GroupNode)tree.getModel().getRoot();
          GroupNode found = null;
          for (GroupNode subGroup : rootNode.getSubGroups()) {
            String subGroupText = subGroup.getGroup().getText(null);
            groupNames.add(subGroupText);
            if (groupText.equals(subGroupText)) {
              found = subGroup;
            }
          }
          return found;
        }
      });
      String msg = String.format("Failed to find usage group '%1$s' in %2$s", groupText, groupNames);
      assertNotNull(msg, foundGroup);
    }

    @NotNull
    private Tree getContentsTree() {
      JComponent component = myContent.getComponent();
      OccurenceNavigatorSupport navigatorSupport = field("mySupport").ofType(OccurenceNavigatorSupport.class).in(component).get();
      assertNotNull(navigatorSupport);
      JTree tree = field("myTree").ofType(JTree.class).in(navigatorSupport).get();
      assertNotNull(tree);
      return (Tree)tree;
    }
  }
}
