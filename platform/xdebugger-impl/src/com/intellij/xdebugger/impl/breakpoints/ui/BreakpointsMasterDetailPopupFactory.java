/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.popup.util.MasterDetailPopupBuilder;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 5/9/12
 * Time: 3:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class BreakpointsMasterDetailPopupFactory {

  private final List<BreakpointPanelProvider> myBreakpointPanelProviders;
  private Project myProject;

  public BreakpointsMasterDetailPopupFactory(Project project) {
    myProject = project;
    myBreakpointPanelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : DebuggerSupport.getDebuggerSupports()) {
      myBreakpointPanelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(myBreakpointPanelProviders, new Comparator<BreakpointPanelProvider>() {
      @Override
      public int compare(BreakpointPanelProvider o1, BreakpointPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
  }

  public static BreakpointsMasterDetailPopupFactory getInstance(Project project) {
    return ServiceManager.getService(project, BreakpointsMasterDetailPopupFactory.class);
  }

  public JBPopup createPopup(@Nullable Object initialBreakpoint) {

    MasterDetailPopupBuilder popupBuilder = new MasterDetailPopupBuilder(myProject);

    DefaultActionGroup actions = getActions(popupBuilder);

    final Collection<XBreakpointGroupingRule> rules = new ArrayList<XBreakpointGroupingRule>();

    for (BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      provider.provideBreakpointsGroupingRules(rules);
    }

    final BreakpointItemsTree tree = BreakpointItemsTree.createTree(rules);

    final ArrayList<BreakpointItem> breakpoints = collectItems();
    tree.buildTree(breakpoints);



    final BreakpointPanelProvider.BreakpointsListener listener = new BreakpointPanelProvider.BreakpointsListener() {
      @Override
      public void breakpointsChanged() {
        tree.buildTree(collectItems());
      }
    };

    for (BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      provider.addListener(listener, myProject);
    }

    final JBPopup popup = popupBuilder.
      setActionsGroup(actions).
      setTree(tree).
      setDelegate(new MasterDetailPopupBuilder.Delegate() {
        @Override
        public String getTitle() {
          return "Breakpoints";
        }

        @Override
        public void handleMnemonic(KeyEvent e, Project project, JBPopup popup) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public JComponent createAccessoryView(Project project) {
          return new JCheckBox();
        }

        @Override
        public Object[] getSelectedItemsInTree() {
          final List<BreakpointItem> res = tree.getSelectedBreakpoints();
          return res.toArray(new Object[res.size()]);
        }
      }).setCloseOnEnter(false).createMasterDetailPopup();

    tree.setBorder(IdeBorderFactory.createBorder());

    popupBuilder.getDetailView().setScheme(createScheme());

    tree.setDelegate(new BreakpointItemsTree.BreakpointItemsTreeDelegate() {
      @Override
      public void execute(BreakpointItem item) {
        item.execute(myProject, popup);
      }
    });

    initSelection(initialBreakpoint, tree, breakpoints);

    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        for (BreakpointPanelProvider provider : myBreakpointPanelProviders) {
          provider.removeListener(listener);
        }
      }
    });

    return popup;
  }

  private void initSelection(Object initialBreakpoint, BreakpointItemsTree tree, ArrayList<BreakpointItem> breakpoints) {
    boolean found = false;
    for (BreakpointItem breakpoint : breakpoints) {
      if (breakpoint.getBreakpoint() == initialBreakpoint) {
        tree.selectBreakpointItem(breakpoint);
        found = true;
        break;
      }
    }

    if (!found && !breakpoints.isEmpty()) {
      tree.selectBreakpointItem(breakpoints.get(0));
    }
  }

  private EditorColorsScheme createScheme() {
    final EditorColorsScheme scheme =
      new EditorColorsSchemeImpl(EditorColorsManager.getInstance().getGlobalScheme(), DefaultColorSchemesManager.getInstance());
    scheme.setName("abc");
    scheme.setAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES, new TextAttributes(Color.black, Color.CYAN, null, EffectType.BOXED, Font.BOLD));
    return scheme;
  }

  private DefaultActionGroup getActions(final MasterDetailPopupBuilder builder) {
    DefaultActionGroup actions = new DefaultActionGroup();
    final DefaultActionGroup breakpointTypes = new DefaultActionGroup();
    for (BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      breakpointTypes.addAll(provider.getAddBreakpointActions(myProject));
    }
    actions.add(new AnAction("Add Breakpoint", null, IconUtil.getAddIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {

        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, breakpointTypes, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false)
          .showUnderneathOf(builder.getActionToolbar().getComponent());
      }
    });
    actions.add(new AnAction("Remove Breakpoint", null, PlatformIcons.DELETE_ICON) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(MasterDetailPopupBuilder.allowedToRemoveItems(builder.getSelectedItems()));
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        builder.removeSelectedItems(myProject);
      }
    });

    return actions;
  }

  private ArrayList<BreakpointItem> collectItems() {
    ArrayList<BreakpointItem> items = new ArrayList<BreakpointItem>();
    for (BreakpointPanelProvider panelProvider : myBreakpointPanelProviders) {
      panelProvider.provideBreakpointItems(myProject, items);
    }
    return items;
  }
}
