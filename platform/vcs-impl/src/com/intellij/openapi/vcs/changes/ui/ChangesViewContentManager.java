/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.*;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * @author yole
 */
public class ChangesViewContentManager extends AbstractProjectComponent implements ChangesViewContentI {
  public static final String TOOLWINDOW_ID = ToolWindowId.VCS;
  private static final Key<ChangesViewContentEP> myEPKey = Key.create("ChangesViewContentEP");

  private MyContentManagerListener myContentManagerListener;
  private final ProjectLevelVcsManager myVcsManager;

  public static ChangesViewContentI getInstance(Project project) {
    return project.getComponent(ChangesViewContentI.class);
  }

  private ContentManager myContentManager;
  private final VcsListener myVcsListener = new MyVcsListener();
  private final Alarm myVcsChangeAlarm;
  private final List<Content> myAddedContents = new ArrayList<>();
  @NotNull private final CountDownLatch myInitializationWaiter = new CountDownLatch(1);

  public ChangesViewContentManager(@NotNull Project project, final ProjectLevelVcsManager vcsManager) {
    super(project);
    myVcsManager = vcsManager;
    myVcsChangeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, myVcsListener);
  }

  public void setUp(ToolWindow toolWindow) {

    final ContentManager contentManager = toolWindow.getContentManager();
    myContentManagerListener = new MyContentManagerListener();
    contentManager.addContentManagerListener(myContentManagerListener);

    Disposer.register(myProject, new Disposable(){
      public void dispose() {
        contentManager.removeContentManagerListener(myContentManagerListener);
      }
    });

    loadExtensionTabs();
    myContentManager = contentManager;
    final List<Content> ordered = doPresetOrdering(myAddedContents);
    for(Content content: ordered) {
      myContentManager.addContent(content);
    }
    myAddedContents.clear();
    if (contentManager.getContentCount() > 0) {
      contentManager.setSelectedContent(contentManager.getContent(0));
    }
    myInitializationWaiter.countDown();
  }

  private void loadExtensionTabs() {
    final List<Content> contentList = new LinkedList<>();
    final ChangesViewContentEP[] contentEPs = myProject.getExtensions(ChangesViewContentEP.EP_NAME);
    for(ChangesViewContentEP ep: contentEPs) {
      final NotNullFunction<Project,Boolean> predicate = ep.newPredicateInstance(myProject);
      if (predicate == null || predicate.fun(myProject).equals(Boolean.TRUE)) {
        final Content content = ContentFactory.SERVICE.getInstance().createContent(new ContentStub(ep), ep.getTabName(), false);
        content.setCloseable(false);
        content.putUserData(myEPKey, ep);
        contentList.add(content);
      }
    }
    myAddedContents.addAll(0, contentList);
  }

  private void addExtensionTab(final ChangesViewContentEP ep) {
    final Content content = ContentFactory.SERVICE.getInstance().createContent(new ContentStub(ep), ep.getTabName(), false);
    content.setCloseable(false);
    content.putUserData(myEPKey, ep);
    addIntoCorrectPlace(content);
  }

  private void updateExtensionTabs() {
    final ChangesViewContentEP[] contentEPs = myProject.getExtensions(ChangesViewContentEP.EP_NAME);
    for(ChangesViewContentEP ep: contentEPs) {
      final NotNullFunction<Project,Boolean> predicate = ep.newPredicateInstance(myProject);
      if (predicate == null) continue;
      Content epContent = findEPContent(ep);
      final Boolean predicateResult = predicate.fun(myProject);
      if (predicateResult.equals(Boolean.TRUE) && epContent == null) {
        addExtensionTab(ep);
      }
      else if (predicateResult.equals(Boolean.FALSE) && epContent != null) {
        myContentManager.removeContent(epContent, true);
      }
    }
  }

  @Nullable
  private Content findEPContent(final ChangesViewContentEP ep) {
    final Content[] contents = myContentManager.getContents();
    for(Content content: contents) {
      if (content.getUserData(myEPKey) == ep) {
        return content;
      }
    }
    return null;
  }

  private void updateToolWindowAvailability() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    if (toolWindow != null) {
      boolean available = isAvailable();
      toolWindow.setShowStripeButton(available);
      toolWindow.setAvailable(available, null);
    }
  }

  public boolean isAvailable() {
    final List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    return mappings.stream().anyMatch(mapping -> !StringUtil.isEmpty(mapping.getVcs()));
  }

  public void projectClosed() {
    for (Content content : myAddedContents) {
      Disposer.dispose(content);
    }
    myAddedContents.clear();

    myVcsChangeAlarm.cancelAllRequests();
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewContentManager";
  }

  public void addContent(Content content) {
    if (myContentManager == null) {
      myAddedContents.add(content);
    }
    else {
      addIntoCorrectPlace(content);
    }
  }

  public void removeContent(final Content content) {
    if (myContentManager != null && (! myContentManager.isDisposed())) { // for unit tests
      myContentManager.removeContent(content, true);
    }
  }

  public void setSelectedContent(final Content content) {
    if (myContentManager == null) return;
    myContentManager.setSelectedContent(content);
  }

  @Nullable
  public <T> T getActiveComponent(final Class<T> aClass) {
    if (myContentManager == null) return null;
    final Content content = myContentManager.getSelectedContent();
    if (content != null && aClass.isInstance(content.getComponent())) {
      //noinspection unchecked
      return (T) content.getComponent();
    }
    return null;
  }
  
  public boolean isContentSelected(@NotNull String contentName) {
    if (myContentManager == null) return false;
    Content selectedContent = myContentManager.getSelectedContent();
    if (selectedContent == null) return false;
    return Comparing.equal(contentName, selectedContent.getTabName());
  }

  public void selectContent(@NotNull String tabName) {
    selectContent(tabName, false);
  }

  public void selectContent(@NotNull String tabName, boolean requestFocus) {
    if (myContentManager == null) return;
    for(Content content: myContentManager.getContents()) {
      if (content.getDisplayName().equals(tabName)) {
        myContentManager.setSelectedContent(content, requestFocus);
        break;
      }
    }
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      myVcsChangeAlarm.cancelAllRequests();
      myVcsChangeAlarm.addRequest(() -> {
        if (myProject.isDisposed()) return;
        updateToolWindowAvailability();
        if (myContentManager != null) {
          updateExtensionTabs();
        }
      }, 100, ModalityState.NON_MODAL);
    }
  }

  private static class ContentStub extends JPanel {
    private final ChangesViewContentEP myEP;

    private ContentStub(final ChangesViewContentEP EP) {
      myEP = EP;
    }

    public ChangesViewContentEP getEP() {
      return myEP;
    }
  }

  private class MyContentManagerListener extends ContentManagerAdapter {
    public void selectionChanged(final ContentManagerEvent event) {
      Content content = event.getContent();
      if (content.getComponent() instanceof ContentStub) {
        ChangesViewContentEP ep = ((ContentStub) content.getComponent()).getEP();
        final ChangesViewContentProvider provider = ep.getInstance(myProject);
        final JComponent contentComponent = provider.initContent();
        content.setComponent(contentComponent);
        content.setDisposer(new Disposable() {
          @Override
          public void dispose() {
            provider.disposeContent();
          }
        });
      }
    }
  }

  public static final String LOCAL_CHANGES = "Local Changes";
  public static final String REPOSITORY = "Repository";
  public static final String INCOMING = "Incoming";
  public static final String SHELF = "Shelf";
  private static final String[] ourPresetOrder = {LOCAL_CHANGES, REPOSITORY, INCOMING, SHELF};
  private static List<Content> doPresetOrdering(final List<Content> contents) {
    final List<Content> result = new ArrayList<>(contents.size());
    for (final String preset : ourPresetOrder) {
      for (Iterator<Content> iterator = contents.iterator(); iterator.hasNext();) {
        final Content current = iterator.next();
        if (preset.equals(current.getTabName())) {
          iterator.remove();
          result.add(current);
        }
      }
    }
    result.addAll(contents);
    return result;
  }

  private void addIntoCorrectPlace(final Content content) {
    final String name = content.getTabName();
    final Content[] contents = myContentManager.getContents();

    int idxOfBeingInserted = -1;
    for (int i = 0; i < ourPresetOrder.length; i++) {
      final String s = ourPresetOrder[i];
      if (s.equals(name)) {
        idxOfBeingInserted = i;
      }
    }
    if (idxOfBeingInserted == -1) {
      myContentManager.addContent(content);
      return;
    }

    final Set<String> existingNames = new HashSet<>();
    for (Content existingContent : contents) {
      existingNames.add(existingContent.getTabName());
    }

    int place = idxOfBeingInserted;
    for (int i = 0; i < idxOfBeingInserted; i++) {
      if (! existingNames.contains(ourPresetOrder[i])) {
        -- place;
      }

    }
    myContentManager.addContent(content, place);
  }
}
