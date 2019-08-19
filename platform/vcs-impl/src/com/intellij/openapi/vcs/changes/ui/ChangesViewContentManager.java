// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.*;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ChangesViewContentManager implements ChangesViewContentI, Disposable {
  public static final String TOOLWINDOW_ID = ToolWindowId.VCS;
  private static final Key<ChangesViewContentEP> myEPKey = Key.create("ChangesViewContentEP");

  private final MyContentManagerListener myContentManagerListener;
  @NotNull private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  public static ChangesViewContentI getInstance(Project project) {
    return project.getComponent(ChangesViewContentI.class);
  }

  private ContentManager myContentManager;
  private final Alarm myVcsChangeAlarm;
  private final List<Content> myAddedContents = new ArrayList<>();

  public ChangesViewContentManager(@NotNull Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myVcsChangeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    myContentManagerListener = new MyContentManagerListener();
    myProject.getMessageBus().connect(this).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new MyVcsListener());
  }

  @Override
  public void setUp(ToolWindow toolWindow) {
    myContentManager = toolWindow.getContentManager();
    myContentManager.addContentManagerListener(myContentManagerListener);
    Disposer.register(this, () -> myContentManager.removeContentManagerListener(myContentManagerListener));

    loadExtensionTabs();

    for (Content content : myAddedContents) {
      addIntoCorrectPlace(content);
    }
    myAddedContents.clear();

    // Ensure that first tab is selected after tabs reordering
    Content firstContent = myContentManager.getContent(0);
    if (firstContent != null) myContentManager.setSelectedContent(firstContent);
  }

  private void loadExtensionTabs() {
    for (ChangesViewContentEP ep : ChangesViewContentEP.EP_NAME.getExtensions(myProject)) {
      final NotNullFunction<Project, Boolean> predicate = ep.newPredicateInstance(myProject);
      boolean shouldShowTab = predicate == null || predicate.fun(myProject);
      if (shouldShowTab) {
        myAddedContents.add(createExtensionTab(ep));
      }
    }
  }

  @NotNull
  private static Content createExtensionTab(@NotNull ChangesViewContentEP ep) {
    final Content content = ContentFactory.SERVICE.getInstance().createContent(new ContentStub(ep), ep.getTabName(), false);
    content.setCloseable(false);
    content.putUserData(myEPKey, ep);
    return content;
  }

  private void updateExtensionTabs() {
    if (myContentManager == null) return;
    for (ChangesViewContentEP ep : ChangesViewContentEP.EP_NAME.getExtensions(myProject)) {
      final NotNullFunction<Project, Boolean> predicate = ep.newPredicateInstance(myProject);
      if (predicate == null) continue;
      Content epContent = ContainerUtil.find(myContentManager.getContents(), content -> content.getUserData(myEPKey) == ep);
      boolean shouldShowTab = predicate.fun(myProject);
      if (shouldShowTab && epContent == null) {
        Content tab = createExtensionTab(ep);
        addIntoCorrectPlace(tab);
      }
      else if (!shouldShowTab && epContent != null) {
        myContentManager.removeContent(epContent, true);
      }
    }
  }

  private void updateToolWindowAvailability() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    if (toolWindow == null) return;
    boolean available = isAvailable();
    if (available && !toolWindow.isAvailable()) {
      toolWindow.setShowStripeButton(true);
    }
    toolWindow.setAvailable(available, null);
  }

  @Override
  public boolean isAvailable() {
    return myVcsManager.hasAnyMappings();
  }

  @Override
  public void dispose() {
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

  @Override
  public void addContent(Content content) {
    if (myContentManager == null) {
      myAddedContents.add(content);
    }
    else {
      addIntoCorrectPlace(content);
    }
  }

  @Override
  public void removeContent(final Content content) {
    if (myContentManager == null || myContentManager.isDisposed()) return;
    myContentManager.removeContent(content, true);
  }

  @Override
  public void setSelectedContent(final Content content) {
    if (myContentManager == null) return;
    myContentManager.setSelectedContent(content);
  }

  @Override
  @Nullable
  public <T> T getActiveComponent(final Class<T> aClass) {
    if (myContentManager == null) return null;
    Content selectedContent = myContentManager.getSelectedContent();
    if (selectedContent == null) return null;
    return ObjectUtils.tryCast(selectedContent.getComponent(), aClass);
  }

  public boolean isContentSelected(@NotNull String contentName) {
    if (myContentManager == null) return false;
    Content selectedContent = myContentManager.getSelectedContent();
    if (selectedContent == null) return false;
    return Comparing.equal(contentName, selectedContent.getTabName());
  }

  @Override
  public void selectContent(@NotNull String tabName) {
    selectContent(tabName, false);
  }

  public void selectContent(@NotNull String tabName, boolean requestFocus) {
    if (myContentManager == null) return;
    Content toSelect = ContainerUtil.find(myContentManager.getContents(), content -> content.getTabName().equals(tabName));
    if (toSelect != null) {
      myContentManager.setSelectedContent(toSelect, requestFocus);
    }
  }

  private class MyVcsListener implements VcsListener {
    @Override
    public void directoryMappingChanged() {
      myVcsChangeAlarm.cancelAllRequests();
      myVcsChangeAlarm.addRequest(() -> {
        if (myProject.isDisposed()) return;
        updateToolWindowAvailability();
        updateExtensionTabs();
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
    @Override
    public void selectionChanged(@NotNull final ContentManagerEvent event) {
      Content content = event.getContent();
      if (content.getComponent() instanceof ContentStub) {
        ChangesViewContentEP ep = ((ContentStub)content.getComponent()).getEP();
        ChangesViewContentProvider provider = ep.getInstance(myProject);
        content.setComponent(provider.initContent());
        content.setDisposer(() -> provider.disposeContent());
      }
    }
  }

  public static final Key<Integer> ORDER_WEIGHT_KEY = Key.create("ChangesView.ContentOrderWeight");
  public static final String LOCAL_CHANGES = "Local Changes";
  public static final String REPOSITORY = "Repository";
  public static final String INCOMING = "Incoming";
  public static final String SHELF = "Shelf";

  public enum TabOrderWeight {
    LOCAL_CHANGES(ChangesViewContentManager.LOCAL_CHANGES, 10),
    REPOSITORY(ChangesViewContentManager.REPOSITORY, 20),
    INCOMING(ChangesViewContentManager.INCOMING, 30),
    SHELF(ChangesViewContentManager.SHELF, 40),
    OTHER(null, 100);

    @Nullable private final String myName;
    private final int myWeight;

    TabOrderWeight(@Nullable String name, int weight) {
      myName = name;
      myWeight = weight;
    }

    @Nullable
    private String getName() {
      return myName;
    }

    public int getWeight() {
      return myWeight;
    }
  }

  private void addIntoCorrectPlace(final Content content) {
    int weight = getContentWeight(content);

    final Content[] contents = myContentManager.getContents();

    int index = -1;
    for (int i = 0; i < contents.length; i++) {
      int oldWeight = getContentWeight(contents[i]);
      if (oldWeight > weight) {
        index = i;
        break;
      }
    }

    if (index == -1) index = contents.length;
    myContentManager.addContent(content, index);
  }

  private static int getContentWeight(Content content) {
    Integer userData = content.getUserData(ORDER_WEIGHT_KEY);
    if (userData != null) return userData;

    String tabName = content.getTabName();
    for (TabOrderWeight value : TabOrderWeight.values()) {
      if (value.getName() != null && value.getName().equals(tabName)) {
        return value.getWeight();
      }
    }

    return TabOrderWeight.OTHER.getWeight();
  }
}
