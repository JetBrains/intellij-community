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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.popup.util.MasterDetailPopupBuilder;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    final DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
    final BreakpointListModel model = createBreakpointsItemsList(selectionModel);
    final JBList list = new JBList(model);
    list.setSelectionModel(selectionModel);
    list.getEmptyText().setText("No Bookmarks");

    DefaultActionGroup actions = getActions();

    final JBPopup popup = new MasterDetailPopupBuilder(myProject).
      setActionsGroup(actions).
      setList(list).
      setDelegate(new MasterDetailPopupBuilder.Delegate() {
        @Override
        public String getTitle() {
          return "Breakpoints";
        }

        @Override
        public void handleMnemonic(KeyEvent e, Project project, JBPopup popup) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void itemRemoved(ItemWrapper item, Project project) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean hasItemsWithMnemonic(Project project) {
          return false;
        }
      }).createMasterDetailPopup();

    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        model.unsubscribe();
      }
    });

    return popup;
  }

  private DefaultActionGroup getActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    final DefaultActionGroup breakpointTypes = new DefaultActionGroup();
    for (BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      breakpointTypes.addAll(provider.getAddBreakpointActions(myProject));
    }
    actions.add(new AnAction("Add Breakpoint", null, PlatformIcons.ADD_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {

        JBPopupFactory.getInstance()
          .createActionGroupPopup("Choose type", breakpointTypes, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false)
          .showInFocusCenter();
      }
    });

    return actions;
  }

  private BreakpointListModel createBreakpointsItemsList(DefaultListSelectionModel selectionModel) {
    final BreakpointListModel model = new BreakpointListModel();
    final ArrayList<ItemWrapper> items = collectItems();
    for (ItemWrapper item : items) {
      model.addElement(item);
    }
    model.subscribe(selectionModel);
    return model;
  }



  private ArrayList<ItemWrapper> collectItems() {
    ArrayList<ItemWrapper> items = new ArrayList<ItemWrapper>();
    for (BreakpointPanelProvider panelProvider : myBreakpointPanelProviders) {
      panelProvider.provideBreakpointItems(myProject, items);
    }
    return items;
  }

  private class BreakpointListModel extends DefaultListModel {
    List<BreakpointPanelProvider.BreakpointsListener> myListeners = new ArrayList<BreakpointPanelProvider.BreakpointsListener>();

    private void subscribe(DefaultListSelectionModel selectionModel) {
      for (BreakpointPanelProvider panelProvider : myBreakpointPanelProviders) {
        final BreakpointPanelProvider.BreakpointsListener listener = new MyBreakpointsListener(this, selectionModel);
        panelProvider.addListener(listener, myProject);
        myListeners.add(listener);
      }
    }

    public void unsubscribe() {
      for (int i = 0, size = myListeners.size(); i < size; i++) {
        BreakpointPanelProvider.BreakpointsListener listener = myListeners.get(i);
        myBreakpointPanelProviders.get(i).removeListener(listener);
      }
    }
  }

  private class MyBreakpointsListener implements BreakpointPanelProvider.BreakpointsListener {
    private final DefaultListModel myModel;
    private DefaultListSelectionModel mySelectionModel;

    public MyBreakpointsListener(DefaultListModel model, DefaultListSelectionModel selectionModel) {
      myModel = model;
      mySelectionModel = selectionModel;
    }

    @Override
    public void breakpointsChanged() {
      final int index = mySelectionModel.getLeadSelectionIndex();
      myModel.removeAllElements();
      final ArrayList<ItemWrapper> items = collectItems();
      for (ItemWrapper item : items) {
        myModel.addElement(item);
      }
      mySelectionModel.setLeadSelectionIndex(index);
    }
  }
}
