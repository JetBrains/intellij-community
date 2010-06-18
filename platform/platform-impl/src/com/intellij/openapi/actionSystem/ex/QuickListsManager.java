/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.BundledQuickListsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;


/**
 * @author max
 */
public class QuickListsManager implements ExportableApplicationComponent, NamedJDOMExternalizable {
  @NonNls private static final String LIST_TAG = "list";
  private final ActionManager myActionManager;
  private final SchemesManager<QuickList, QuickList> mySchemesManager;

  private final static Logger LOG = Logger.getInstance("#" + QuickListsManager.class.getName());

  public static QuickListsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickListsManager.class);
  }

  public QuickListsManager(ActionManagerEx actionManagerEx, SchemesManagerFactory schemesManagerFactory) {
    myActionManager = actionManagerEx;
    mySchemesManager = schemesManagerFactory.createSchemesManager(
        "$ROOT_CONFIG$/quicklists",
        new BaseSchemeProcessor<QuickList>(){
          public QuickList readScheme(final Document schemeContent) throws InvalidDataException, IOException, JDOMException {
            return loadListFromDocument(schemeContent);
          }

          public Document writeScheme(final QuickList scheme) throws WriteExternalException {
            Element element = new Element(LIST_TAG);
            scheme.writeExternal(element);
            return new Document(element);
          }

          public boolean shouldBeSaved(final QuickList scheme) {
            return true;
          }
        },
        RoamingType.PER_USER);

    loadAdditionalDefaultSchemes();

    registerActions();
  }

  private QuickList loadListFromDocument(Document schemeContent) {
    QuickList list = new QuickList();
    list.readExternal(schemeContent.getRootElement());
    return list;
  }

  @NotNull
  public String getComponentName() {
    return "QuickListsManager";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("quick.lists.presentable.name");
  }

  public void initComponent() {
    mySchemesManager.loadSchemes();
    registerActions();    
  }

  public void disposeComponent() {
  }

  public String getExternalFileName() {
    return "quicklists";
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (Object group : element.getChildren(LIST_TAG)) {
      Element groupElement = (Element)group;
      QuickList list = new QuickList();
      list.readExternal(groupElement);
      mySchemesManager.addNewScheme(list, true);
    }
    mySchemesManager.loadSchemes();
    registerActions();
  }

  public void writeExternal(Element element) throws WriteExternalException {

  }

  public QuickList[] getAllQuickLists() {
    Collection<QuickList> lists = mySchemesManager.getAllSchemes();
    return lists.toArray(new QuickList[lists.size()]);
  }

  public void removeAllQuickLists() {
    mySchemesManager.clearAllSchemes();
  }

  public void registerActions() {
    unregisterActions();
    HashSet<String> registeredIds = new HashSet<String>(); // to prevent exception if 2 or more targets have the same name

    ActionManager actionManager = myActionManager;
    for (QuickList list : mySchemesManager.getAllSchemes()) {
      String actionId = list.getActionId();

      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId);
        actionManager.registerAction(actionId, new InvokeQuickListAction(list));
      }
    }
  }

  private void unregisterActions() {
    ActionManagerEx actionManager = (ActionManagerEx)myActionManager;

    for (String oldId : actionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {
      actionManager.unregisterAction(oldId);
    }
  }

  public void registerQuickList(final QuickList quickList) {
    mySchemesManager.addNewScheme(quickList, true);
  }

  public SchemesManager<QuickList, QuickList> getSchemesManager() {
    return mySchemesManager;
  }


  private void loadAdditionalDefaultSchemes() {
    //Get color schemes from EPs
    for (BundledQuickListsProvider provider : BundledQuickListsProvider.EP_NAME.getExtensions()) {
      final String[] paths = provider.getBundledListsRelativePaths();

      for (final String path : paths) {
        try {
          final InputStream inputStream = DecodeDefaultsUtil.getDefaultsInputStream(provider, path);
          if (inputStream == null) {
            // Error shouldn't occur during this operation
            // thus we report error instead of info
            LOG.error("Cannot read quick list from " +  path);
            continue;
          }

          final Document document;
          try {
            document = JDOMUtil.loadDocument(inputStream);
          }
          catch (JDOMException e) {
            LOG.info("Error reading quick list from  " + path + ": " + e.getLocalizedMessage());
            throw e;
          }
          final QuickList scheme = loadListFromDocument(document);
          mySchemesManager.addNewScheme(scheme, false);
        }
        catch (final Exception e) {
          ApplicationManager.getApplication().invokeLater(
            new Runnable(){
              public void run() {
                // Error shouldn't occur during this operation
                // thus we report error instead of info
                LOG.error("Cannot read quick list from " + path + ": " + e.getLocalizedMessage(), e);
              }
            }
          );
        }
      }
    }
  }

  private static class InvokeQuickListAction extends QuickSwitchSchemeAction {
    private final QuickList myQuickList;

    public InvokeQuickListAction(QuickList quickList) {
      myQuickList = quickList;
      getTemplatePresentation().setDescription(myQuickList.getDescription());
      getTemplatePresentation().setText(myQuickList.getDisplayName(), false);
    }

    protected void fillActions(Project project, DefaultActionGroup group, DataContext dataContext) {
      ActionManager actionManager = ActionManagerEx.getInstance();
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

    protected boolean isEnabled() {
      return true;
    }
  }
}
