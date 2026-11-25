// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadConstraint;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JTree;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "TodoView", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE), perClient = true)
public class TodoView implements PersistentStateComponent<TodoView.State>, Disposable {

  private final @NotNull Project myProject;

  private ToolWindow myToolWindow;
  private ContentManager myContentManager;
  private TodoPanel myAllTodos;
  private CurrentFileTodosPanel myCurrentFileTodosPanel;
  private final List<TodoPanel> myPanels = new ArrayList<>();
  private final List<Content> myNotAddedContent = new ArrayList<>();
  private volatile boolean myDisposed;

  private State state = new State();

  private final TodoViewChangesSupport myChangesSupport;
  private final TodoViewChangesSupport.Listener myChangesListener;
  private Content myChangeListTodosContent;

  public TodoView(@NotNull Project project) {
    myProject = project;

    state.all.arePackagesShown = true;

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(TodoConfigurationPropertyChangeListener.TOPIC, new MyPropertyChangeListener());
    connection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());

    myChangesSupport = TodoViewChangesSupport.getInstance(project);
    myChangesListener = myChangesSupport.installListener(project, connection,
                                                         () -> myContentManager,
                                                         () -> myChangeListTodosContent);
  }

  void setSelectedContent(@NotNull TodoPanel panel) {
    Content content = myContentManager.getContent(panel);
    if (content != null) {
      myContentManager.setSelectedContent(content);
    }
  }

  static final class State {
    @Attribute("selected-index")
    public int selectedIndex;

    @OptionTag(value = "selected-file", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings current = new TodoPanelSettings();

    @OptionTag(value = "all", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings all = new TodoPanelSettings();

    @OptionTag(value = "default-changelist", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
    public TodoPanelSettings changeList = new TodoPanelSettings();

    public @Nls String selectedScope;
  }

  @NotNull Project getProject() {
    return myProject;
  }

  @ApiStatus.Internal
  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull State getState() {
    if (myContentManager != null) {
      // all panel were constructed
      Content content = myContentManager.getSelectedContent();
      state.selectedIndex = content == null ? -1 : myContentManager.getIndexOfContent(content);
    }
    return state;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @TestOnly
  @ApiStatus.Internal
  public enum Scope {
    AllTodos,
    ChangeList,
    CurrentFile,
    ScopeBased
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    // Create panels
    ContentFactory contentFactory = ContentFactory.getInstance();
    Content allTodosContent =
      contentFactory.createContent(null, IdeUICustomization.getInstance().projectMessage("tab.title.project"), false);
    toolWindow.setHelpId("find.todoList");
    myAllTodos = new TodoPanel(this, state.all, false, allTodosContent) {
      @Override
      protected TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                  @NotNull Project project) {
        AllTodosTreeBuilder builder = createAllTodoBuilder(tree, project);
        builder.init();
        return builder;
      }
    };
    allTodosContent.setComponent(myAllTodos);
    allTodosContent.setPreferredFocusableComponent(myAllTodos.getTree());
    Disposer.register(this, myAllTodos);
    if (toolWindow instanceof ToolWindowEx) {
      DefaultActionGroup group = new DefaultActionGroup() {
        {
          getTemplatePresentation().setText(IdeBundle.messagePointer("group.view.options"));
          setPopup(true);
          add(myAllTodos.createAutoScrollToSourceAction());
          addSeparator();
          addAll(myAllTodos.createGroupByActionGroup());
        }
      };
      toolWindow.setAdditionalGearActions(group);
    }

    Content currentFileTodosContent = contentFactory.createContent(null, IdeBundle.message("title.todo.current.file"), false);
    myCurrentFileTodosPanel = new CurrentFileTodosPanel(this, state.current, currentFileTodosContent) {
      @Override
      protected TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                  @NotNull Project project) {
        CurrentFileTodosTreeBuilder builder = new CurrentFileTodosTreeBuilder(tree, project);
        builder.init();
        return builder;
      }
    };
    Disposer.register(this, myCurrentFileTodosPanel);
    currentFileTodosContent.setComponent(myCurrentFileTodosPanel);
    currentFileTodosContent.setPreferredFocusableComponent(myCurrentFileTodosPanel.getTree());

    String tabName = myChangesSupport.getTabName(myProject);
    myChangeListTodosContent = contentFactory.createContent(null, tabName, false);
    TodoPanel changeListTodosPanel = myChangesSupport.createPanel(this, state.current, myChangeListTodosContent);
    if (changeListTodosPanel != null) {
      Disposer.register(this, changeListTodosPanel);
      myChangeListTodosContent.setComponent(changeListTodosPanel);
      myChangeListTodosContent.setPreferredFocusableComponent(changeListTodosPanel.getTree());
    }

    Content scopeBasedTodoContent = contentFactory.createContent(null, LangBundle.message("tab.title.scope.based"), false);
    ScopeBasedTodosPanel scopeBasedTodosPanel = new ScopeBasedTodosPanel(this, state.current, scopeBasedTodoContent);
    Disposer.register(this, scopeBasedTodosPanel);
    scopeBasedTodoContent.setComponent(scopeBasedTodosPanel);

    myToolWindow = toolWindow;
    myContentManager = toolWindow.getContentManager();

    myContentManager.addContent(allTodosContent);
    myContentManager.addContent(currentFileTodosContent);
    myContentManager.addContent(scopeBasedTodoContent);

    if (myChangesSupport.isContentVisible(myProject)) {
      myChangesListener.setVisible(true);
      myContentManager.addContent(myChangeListTodosContent);
    }
    for (Content content : myNotAddedContent) {
      myContentManager.addContent(content);
    }

    myChangeListTodosContent.setCloseable(false);
    allTodosContent.setCloseable(false);
    currentFileTodosContent.setCloseable(false);
    scopeBasedTodoContent.setCloseable(false);
    Content content = myContentManager.getContent(state.selectedIndex);
    myContentManager.setSelectedContent(content == null ? allTodosContent : content);

    myPanels.add(myAllTodos);
    if (changeListTodosPanel != null) {
      myPanels.add(changeListTodosPanel);
    }
    myPanels.add(myCurrentFileTodosPanel);
    myPanels.add(scopeBasedTodosPanel);

    MyVisibilityListener visibilityListener = new MyVisibilityListener();
    myProject.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, visibilityListener);
    toolWindow.addContentManagerListener(visibilityListener);

    myAllTodos.invokePostponedRunnable();
    myCurrentFileTodosPanel.invokePostponedRunnable();
    if (changeListTodosPanel != null) {
      changeListTodosPanel.invokePostponedRunnable();
    }
  }

  @ApiStatus.Internal
  protected @NotNull AllTodosTreeBuilder createAllTodoBuilder(@NotNull JTree tree,
                                                              @NotNull Project project) {
    return new AllTodosTreeBuilder(tree, project);
  }

  private final class MyPropertyChangeListener implements TodoConfigurationPropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent e) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(e.getPropertyName()) ||
          TodoConfiguration.PROP_TODO_FILTERS.equals(e.getPropertyName())) {
        updateFilters();
      }
    }

    private void updateFilters() {
      try {
        for (TodoPanel panel : myPanels) {
          panel.updateTodoFilter();
        }
      }
      catch (ProcessCanceledException ignore) {
      }
    }
  }

  private final class MyFileTypeListener implements FileTypeListener {
    private final AtomicBoolean myRefreshScheduled = new AtomicBoolean();

    @Override
    public void fileTypesChanged(@NotNull FileTypeEvent e) {
      if (myRefreshScheduled.compareAndSet(false, true)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          myRefreshScheduled.set(false);
          refresh();
        }, ModalityState.defaultModalityState(), _ -> myDisposed || myProject.isDisposed());
      }
    }
  }

  private final class MyVisibilityListener implements ToolWindowManagerListener, ContentManagerListener {
    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      visibilityChanged();
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      visibilityChanged();
    }
  }

  private void visibilityChanged() {
    if (myProject.isOpen()) {
      PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(
        () -> myPanels.forEach(p -> p.updateVisibility(myToolWindow)));
    }
  }

  public final void refresh(@NotNull List<? extends VirtualFile> files) {
    if (myDisposed || myAllTodos == null) {
      return;
    }

    myPanels.stream()
      .map(TodoPanel::getTreeBuilder)
      .map(TodoTreeBuilder::getCoroutineHelper)
      .forEach(x -> x.scheduleMarkFilesAsDirtyAndUpdateTree(files));
  }

  public TodoPanel getCurrentFilePanel() {
    return myCurrentFileTodosPanel;
  }

  @VisibleForTesting
  public final @NotNull CompletableFuture<Void> refresh() {
    if (myDisposed || myAllTodos == null) {
      return CompletableFuture.completedFuture(null);
    }

    ReadConstraint inSmartMode = ReadConstraint.Companion.inSmartMode(myProject);
    CompletableFuture<?>[] futures = myPanels.stream()
      .map(TodoPanel::getTreeBuilder)
      .map(TodoTreeBuilder::getCoroutineHelper)
      .map(helper -> helper.scheduleCacheAndTreeUpdate(inSmartMode))
      .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }

  @ApiStatus.Internal
  public @Nullable Content addCustomTodoView(@NotNull TodoTreeBuilderFactory factory,
                                             @NlsContexts.TabTitle String title,
                                             @NotNull TodoPanelSettings settings) {
    Content content = ContentFactory.getInstance().createContent(null, title, true);
    final TodoPanel panel = myChangesSupport.createPanel(this, settings, content, factory);
    if (panel == null) return null;

    content.setComponent(panel);
    Disposer.register(this, panel);

    if (myContentManager == null) {
      myNotAddedContent.add(content);
    }
    else {
      myContentManager.addContent(content);
    }
    myPanels.add(panel);
    content.setCloseable(true);
    content.setDisposer(() -> myPanels.remove(panel));
    return content;
  }
}
