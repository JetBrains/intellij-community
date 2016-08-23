/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.BundledQuickListsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.project.Project;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class QuickListsManager implements ExportableApplicationComponent {
  private static final String LIST_TAG = "list";

  private final ActionManager myActionManager;
  private final SchemeManager<QuickList> mySchemeManager;

  public QuickListsManager(@NotNull ActionManager actionManager, @NotNull SchemeManagerFactory schemeManagerFactory) {
    myActionManager = actionManager;
    mySchemeManager = schemeManagerFactory.create("quicklists", new NonLazySchemeProcessor<QuickList, QuickList>() {
      @NotNull
      @Override
      public QuickList readScheme(@NotNull Element element, boolean duringLoad) {
        return createItem(element);
      }

      @NotNull
      @Override
      public Element writeScheme(@NotNull QuickList scheme) {
        Element element = new Element(LIST_TAG);
        scheme.writeExternal(element);
        return element;
      }
    });
  }

  @NotNull
  public static QuickListsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickListsManager.class);
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{mySchemeManager.getRootDirectory()};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return IdeBundle.message("quick.lists.presentable.name");
  }

  @NotNull
  private static QuickList createItem(@NotNull Element element) {
    QuickList item = new QuickList();
    item.readExternal(element);
    return item;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "QuickListsManager";
  }

  @Override
  public void initComponent() {
    for (BundledQuickListsProvider provider : BundledQuickListsProvider.EP_NAME.getExtensions()) {
      for (final String path : provider.getBundledListsRelativePaths()) {
        mySchemeManager.loadBundledScheme(path, provider, QuickListsManager::createItem);
      }
    }
    mySchemeManager.loadSchemes();
    registerActions();
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  public SchemeManager<QuickList> getSchemeManager() {
    return mySchemeManager;
  }

  @NotNull
  public QuickList[] getAllQuickLists() {
    Collection<QuickList> lists = mySchemeManager.getAllSchemes();
    return lists.toArray(new QuickList[lists.size()]);
  }

  private void registerActions() {
    // to prevent exception if 2 or more targets have the same name
    Set<String> registeredIds = new THashSet<>();
    for (QuickList list : mySchemeManager.getAllSchemes()) {
      String actionId = list.getActionId();
      if (registeredIds.add(actionId)) {
        myActionManager.registerAction(actionId, new InvokeQuickListAction(list));
      }
    }
  }

  private void unregisterActions() {
    for (String oldId : myActionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {
      myActionManager.unregisterAction(oldId);
    }
  }

  public void setQuickLists(@NotNull List<QuickList> quickLists) {
    unregisterActions();
    mySchemeManager.setSchemes(quickLists);
    registerActions();
  }

  private static class InvokeQuickListAction extends QuickSwitchSchemeAction {
    private final QuickList myQuickList;

    public InvokeQuickListAction(@NotNull QuickList quickList) {
      myQuickList = quickList;
      myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION;
      getTemplatePresentation().setDescription(myQuickList.getDescription());
      getTemplatePresentation().setText(myQuickList.getName(), false);
    }

    @Override
    protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
      ActionManager actionManager = ActionManager.getInstance();
      for (String actionId : myQuickList.getActionIds()) {
        if (QuickList.SEPARATOR_ID.equals(actionId)) {
          group.addSeparator();
        }
        else {
          AnAction action = actionManager.getAction(actionId);
          if (action != null) {
            group.add(action);
          }
        }
      }
    }

    @Override
    protected boolean isEnabled() {
      return true;
    }
  }
}
