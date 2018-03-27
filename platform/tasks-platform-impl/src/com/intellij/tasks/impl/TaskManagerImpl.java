/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.tasks.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.tasks.*;
import com.intellij.tasks.context.WorkingContextManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * @author Dmitry Avdeev
 */
@State(
  name = "TaskManager",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class TaskManagerImpl extends TaskManager implements ProjectComponent, PersistentStateComponent<TaskManagerImpl.Config>,
                                                            ChangeListDecorator {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.impl.TaskManagerImpl");

  private static final DecimalFormat LOCAL_TASK_ID_FORMAT = new DecimalFormat("LOCAL-00000");
  public static final Comparator<Task> TASK_UPDATE_COMPARATOR = (o1, o2) -> {
    int i = Comparing.compare(o2.getUpdated(), o1.getUpdated());
    return i == 0 ? Comparing.compare(o2.getCreated(), o1.getCreated()) : i;
  };
  private static final Convertor<Task, String> KEY_CONVERTOR = o -> o.getId();

  private final Project myProject;

  private final WorkingContextManager myContextManager;

  private final Map<String, Task> myIssueCache = Collections.synchronizedMap(new LinkedHashMap<String, Task>());

  private final Map<String, LocalTask> myTasks = Collections.synchronizedMap(new LinkedHashMap<String, LocalTask>() {
    @Override
    public LocalTask put(String key, LocalTask task) {
      LocalTask result = super.put(key, task);
      if (size() > myConfig.taskHistoryLength) {
        ArrayList<Map.Entry<String, LocalTask>> list = new ArrayList<>(entrySet());
        Collections.sort(list, (o1, o2) -> TASK_UPDATE_COMPARATOR.compare(o2.getValue(), o1.getValue()));
        for (Map.Entry<String, LocalTask> oldest : list) {
          if (!oldest.getValue().isDefault()) {
            remove(oldest.getKey());
            break;
          }
        }
      }
      return result;
    }
  });

  @NotNull
  private LocalTask myActiveTask = createDefaultTask();
  private Timer myCacheRefreshTimer;

  private volatile boolean myUpdating;
  private final Config myConfig = new Config();
  private final ChangeListAdapter myChangeListListener;
  private final ChangeListManager myChangeListManager;

  private final List<TaskRepository> myRepositories = new ArrayList<>();
  private final EventDispatcher<TaskListener> myDispatcher = EventDispatcher.create(TaskListener.class);
  private Set<TaskRepository> myBadRepositories = ContainerUtil.newConcurrentSet();

  public TaskManagerImpl(Project project, WorkingContextManager contextManager, ChangeListManager changeListManager) {

    myProject = project;
    myContextManager = contextManager;
    myChangeListManager = changeListManager;

    myChangeListListener = new ChangeListAdapter() {
      @Override
      public void changeListRemoved(ChangeList list) {
        LocalTask task = getAssociatedTask((LocalChangeList)list);
        if (task != null) {
          for (ChangeListInfo info : task.getChangeLists()) {
            if (Comparing.equal(info.id, ((LocalChangeList)list).getId())) {
              info.id = "";
            }
          }
        }
      }

      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
        final LocalTask associatedTask = getAssociatedTask((LocalChangeList)newDefaultList);
        if (associatedTask != null && !getActiveTask().equals(associatedTask)) {
          ApplicationManager.getApplication().invokeLater(() -> activateTask(associatedTask, true), myProject.getDisposed());
        }
      }
    };
  }

  @Override
  public TaskRepository[] getAllRepositories() {
    return myRepositories.toArray(new TaskRepository[myRepositories.size()]);
  }

  public <T extends TaskRepository> void setRepositories(List<T> repositories) {

    Set<TaskRepository> set = new HashSet<>(myRepositories);
    set.removeAll(repositories);
    myBadRepositories.removeAll(set); // remove all changed reps
    myIssueCache.clear();

    myRepositories.clear();
    myRepositories.addAll(repositories);

    reps:
    for (T repository : repositories) {
      if (repository.isShared() && repository.getUrl() != null) {
        List<TaskProjectConfiguration.SharedServer> servers = getProjectConfiguration().servers;
        TaskRepositoryType type = repository.getRepositoryType();
        for (TaskProjectConfiguration.SharedServer server : servers) {
          if (repository.getUrl().equals(server.url) && type.getName().equals(server.type)) {
            continue reps;
          }
        }
        TaskProjectConfiguration.SharedServer server = new TaskProjectConfiguration.SharedServer();
        server.type = type.getName();
        server.url = repository.getUrl();
        servers.add(server);
      }
    }
  }

  @Override
  public void removeTask(LocalTask task) {
    if (task.isDefault()) return;
    if (myActiveTask.equals(task)) {
      activateTask(myTasks.get(LocalTaskImpl.DEFAULT_TASK_ID), true);
    }
    myTasks.remove(task.getId());
    myDispatcher.getMulticaster().taskRemoved(task);
    myContextManager.removeContext(task);
  }

  @Override
  public void addTaskListener(TaskListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addTaskListener(@NotNull TaskListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void removeTaskListener(TaskListener listener) {
    myDispatcher.removeListener(listener);
  }

  @NotNull
  @Override
  public LocalTask getActiveTask() {
    return myActiveTask;
  }

  @Nullable
  @Override
  public LocalTask findTask(String id) {
    return myTasks.get(id);
  }

  @NotNull
  @Override
  public List<Task> getIssues(@Nullable final String query) {
    return getIssues(query, true);
  }

  @Override
  public List<Task> getIssues(@Nullable final String query, final boolean forceRequest) {
    return getIssues(query, 0, 50, true, new EmptyProgressIndicator(), forceRequest);
  }

  @Override
  public List<Task> getIssues(@Nullable String query,
                              int offset,
                              int limit,
                              final boolean withClosed,
                              @NotNull ProgressIndicator indicator,
                              boolean forceRequest) {
    List<Task> tasks = getIssuesFromRepositories(query, offset, limit, withClosed, forceRequest, indicator);
    if (tasks == null) {
      return getCachedIssues(withClosed);
    }
    myIssueCache.putAll(ContainerUtil.newMapFromValues(tasks.iterator(), KEY_CONVERTOR));
    return ContainerUtil.filter(tasks, task -> withClosed || !task.isClosed());
  }

  @Override
  public List<Task> getCachedIssues() {
    return getCachedIssues(true);
  }

  @Override
  public List<Task> getCachedIssues(final boolean withClosed) {
    return ContainerUtil.filter(myIssueCache.values(), task -> withClosed || !task.isClosed());
  }

  private void updateIssue(@NotNull String id) {
    for (TaskRepository repository : getAllRepositories()) {
      if (repository.extractId(id) == null) {
        continue;
      }
      try {
        LOG.info("Searching for task '" + id + "' in " + repository);
        Task issue = repository.findTask(id);
        if (issue != null) {
          LocalTask localTask = findTask(id);
          if (localTask != null) {
            localTask.updateFromIssue(issue);
            return;
          }
          return;
        }
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }
  }

  @Override
  public List<LocalTask> getLocalTasks() {
    return getLocalTasks(true);
  }

  @Override
  public List<LocalTask> getLocalTasks(final boolean withClosed) {
    synchronized (myTasks) {
      return ContainerUtil.filter(myTasks.values(), task -> withClosed || !isLocallyClosed(task));
    }
  }

  @Override
  public LocalTask addTask(Task issue) {
    LocalTaskImpl task = issue instanceof LocalTaskImpl ? (LocalTaskImpl)issue : new LocalTaskImpl(issue);
    addTask(task);
    return task;
  }

  @Override
  public LocalTaskImpl createLocalTask(@NotNull String summary) {
    return createTask(LOCAL_TASK_ID_FORMAT.format(myConfig.localTasksCounter++), summary);
  }

  private static LocalTaskImpl createTask(@NotNull String id, @NotNull String summary) {
    LocalTaskImpl task = new LocalTaskImpl(id, summary);
    Date date = new Date();
    task.setCreated(date);
    task.setUpdated(date);
    return task;
  }

  @Override
  public LocalTask activateTask(@NotNull final Task origin, boolean clearContext) {
    LocalTask activeTask = getActiveTask();
    if (origin.equals(activeTask)) return activeTask;

    saveActiveTask();

    if (clearContext) {
      myContextManager.clearContext();
    }
    myContextManager.restoreContext(origin);

    final LocalTask task = doActivate(origin, true);

    return restoreVcsContext(task);
  }

  private LocalTask restoreVcsContext(LocalTask task) {
    if (!isVcsEnabled()) return task;

    List<ChangeListInfo> changeLists = task.getChangeLists();
    if (!changeLists.isEmpty()) {
      ChangeListInfo info = changeLists.get(0);
      LocalChangeList changeList = myChangeListManager.getChangeList(info.id);
      if (changeList == null) {
        changeList = myChangeListManager.addChangeList(info.name, info.comment);
        info.id = changeList.getId();
      }
      myChangeListManager.setDefaultChangeList(changeList);
    }

    unshelveChanges(task);
    List<BranchInfo> branches = task.getBranches(false);
    // we should have exactly one branch per repo
    MultiMap<String, BranchInfo> multiMap = new MultiMap<>();
    for (BranchInfo branch : branches) {
      multiMap.putValue(branch.repository, branch);
    }
    for (String repo: multiMap.keySet()) {
      Collection<BranchInfo> infos = multiMap.get(repo);
      if (infos.size() > 1) {
        // cleanup needed
        List<BranchInfo> existing = getAllBranches(repo);
        for (Iterator<BranchInfo> iterator = infos.iterator(); iterator.hasNext(); ) {
          BranchInfo info = iterator.next();
          if (!existing.contains(info)) {
            iterator.remove();
            if (infos.size() == 1) {
              break;
            }
          }
        }
      }
    }

    VcsTaskHandler.TaskInfo info = fromBranches(new ArrayList<>(multiMap.values()));

    switchBranch(info);
    return task;
  }

  public void shelveChanges(LocalTask task, @NotNull String shelfName) {
    Collection<Change> changes = ChangeListManager.getInstance(myProject).getDefaultChangeList().getChanges();
    if (changes.isEmpty()) return;
    try {
      ShelveChangesManager.getInstance(myProject).shelveChanges(changes, shelfName, true);
      task.setShelfName(shelfName);
    }
    catch (Exception e) {
      LOG.warn("Can't shelve changes", e);
    }
  }

  private void unshelveChanges(LocalTask task) {
    String name = task.getShelfName();
    if (name != null) {
      ShelveChangesManager manager = ShelveChangesManager.getInstance(myProject);
      for (ShelvedChangeList list : manager.getShelvedChangeLists()) {
        if (name.equals(list.DESCRIPTION)) {
          manager.unshelveChangeList(list, list.getChanges(myProject), list.getBinaryFiles(), myChangeListManager.getDefaultChangeList(), true);
          return;
        }
      }
    }
  }

  private List<BranchInfo> getAllBranches(final String repo) {
    ArrayList<BranchInfo> infos = new ArrayList<>();
    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(myProject);
    for (VcsTaskHandler handler : handlers) {
      VcsTaskHandler.TaskInfo[] tasks = handler.getAllExistingTasks();
      for (VcsTaskHandler.TaskInfo info : tasks) {
        infos.addAll(ContainerUtil.filter(BranchInfo.fromTaskInfo(info, false), info1 -> Comparing.equal(info1.repository, repo)));
      }
    }
    return infos;
  }

  private void switchBranch(VcsTaskHandler.TaskInfo info) {
    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(myProject);
    for (VcsTaskHandler handler : handlers) {
      handler.switchToTask(info, null);
    }
  }

  private static VcsTaskHandler.TaskInfo fromBranches(List<BranchInfo> branches) {
    if (branches.isEmpty()) return new VcsTaskHandler.TaskInfo(null, Collections.emptyList());
    MultiMap<String, String> map = new MultiMap<>();
    for (BranchInfo branch : branches) {
      map.putValue(branch.name, branch.repository);
    }
    Map.Entry<String, Collection<String>> next = map.entrySet().iterator().next();
    return new VcsTaskHandler.TaskInfo(next.getKey(), next.getValue());
  }

  public void createBranch(LocalTask task, LocalTask previousActive, String name, @Nullable VcsTaskHandler.TaskInfo branchFrom) {
    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(myProject);
    for (VcsTaskHandler handler : handlers) {
      VcsTaskHandler.TaskInfo[] info = handler.getCurrentTasks();
      if (previousActive != null && previousActive.getBranches(false).isEmpty()) {
        addBranches(previousActive, info, false);
      }
      addBranches(task, info, true);
      if (info.length == 0 && branchFrom != null) {
        addBranches(task, new VcsTaskHandler.TaskInfo[] { branchFrom }, true);
      }
      addBranches(task, new VcsTaskHandler.TaskInfo[] { handler.startNewTask(name) }, false);
    }
  }

  public void mergeBranch(LocalTask task) {
    VcsTaskHandler.TaskInfo original = fromBranches(task.getBranches(true));
    VcsTaskHandler.TaskInfo feature = fromBranches(task.getBranches(false));

    VcsTaskHandler[] handlers = VcsTaskHandler.getAllHandlers(myProject);
    for (VcsTaskHandler handler : handlers) {
      handler.closeTask(feature, original);
    }
  }

  public static void addBranches(LocalTask task, VcsTaskHandler.TaskInfo[] info, boolean original) {
    for (VcsTaskHandler.TaskInfo taskInfo : info) {
      List<BranchInfo> branchInfos = BranchInfo.fromTaskInfo(taskInfo, original);
      for (BranchInfo branchInfo : branchInfos) {
        task.addBranch(branchInfo);
      }
    }
  }

  private void saveActiveTask() {
    myContextManager.saveContext(myActiveTask);
    myActiveTask.setUpdated(new Date());
    String shelfName = myActiveTask.getShelfName();
    if (shelfName != null) {
      shelveChanges(myActiveTask, shelfName);
    }
  }

  private LocalTask doActivate(Task origin, boolean explicitly) {
    final LocalTaskImpl task = origin instanceof LocalTaskImpl ? (LocalTaskImpl)origin : new LocalTaskImpl(origin);
    if (explicitly) {
      task.setUpdated(new Date());
    }
    myActiveTask.setActive(false);
    task.setActive(true);
    addTask(task);
    if (task.isIssue()) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
        () -> ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Backgroundable(myProject, "Updating " + task.getPresentableId()) {

          public void run(@NotNull ProgressIndicator indicator) {
            updateIssue(task.getId());
          }
        }));
    }
    LocalTask oldActiveTask = myActiveTask;
    boolean isChanged = !task.equals(oldActiveTask);
    myActiveTask = task;
    if (isChanged) {
      myDispatcher.getMulticaster().taskDeactivated(oldActiveTask);
      myDispatcher.getMulticaster().taskActivated(task);
    }
    return task;
  }

  private void addTask(LocalTaskImpl task) {
    myTasks.put(task.getId(), task);
    myDispatcher.getMulticaster().taskAdded(task);
  }

  @Override
  public boolean testConnection(final TaskRepository repository) {

    TestConnectionTask task = new TestConnectionTask("Test connection") {
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText("Connecting to " + repository.getUrl() + "...");
        indicator.setFraction(0);
        indicator.setIndeterminate(true);
        try {
          myConnection = repository.createCancellableConnection();
          if (myConnection != null) {
            Future<Exception> future = ApplicationManager.getApplication().executeOnPooledThread(myConnection);
            while (true) {
              try {
                myException = future.get(100, TimeUnit.MILLISECONDS);
                return;
              }
              catch (TimeoutException ignore) {
                try {
                  indicator.checkCanceled();
                }
                catch (ProcessCanceledException e) {
                  myException = e;
                  myConnection.cancel();
                  return;
                }
              }
              catch (Exception e) {
                myException = e;
                return;
              }
            }
          }
          else {
            try {
              repository.testConnection();
            }
            catch (Exception e) {
              LOG.info(e);
              myException = e;
            }
          }
        }
        catch (Exception e) {
          myException = e;
        }
      }
    };
    ProgressManager.getInstance().run(task);
    Exception e = task.myException;
    if (e == null) {
      myBadRepositories.remove(repository);
      Messages.showMessageDialog(myProject, "Connection is successful", "Connection", Messages.getInformationIcon());
    }
    else if (!(e instanceof ProcessCanceledException)) {
      String message = e.getMessage();
      if (e instanceof UnknownHostException) {
        message = "Unknown host: " + message;
      }
      if (message == null) {
        LOG.error(e);
        message = "Unknown error";
      }
      Messages.showErrorDialog(myProject, StringUtil.capitalize(message), "Error");
    }
    return e == null;
  }

  @NotNull
  public Config getState() {
    myConfig.tasks = ContainerUtil.map(myTasks.values(), (Function<Task, LocalTaskImpl>)task -> new LocalTaskImpl(task));
    myConfig.servers = XmlSerializer.serialize(getAllRepositories());
    return myConfig;
  }

  public void loadState(Config config) {
    XmlSerializerUtil.copyBean(config, myConfig);

    myRepositories.clear();
    Element element = config.servers;
    List<TaskRepository> repositories = loadRepositories(element);
    myRepositories.addAll(repositories);

    myTasks.clear();
    for (LocalTaskImpl task : config.tasks) {
      if (task.getRepository() == null) {
        // restore repository from url
        String url = task.getIssueUrl();
        if (url != null) {
          for (TaskRepository repository : repositories) {
            if (repository.getUrl() != null && url.startsWith(repository.getUrl())) {
              task.setRepository(repository);
            }
          }
        }
      }
      addTask(task);
    }
  }

  public static ArrayList<TaskRepository> loadRepositories(Element element) {
    ArrayList<TaskRepository> repositories = new ArrayList<>();
    for (TaskRepositoryType repositoryType : TaskRepositoryType.getRepositoryTypes()) {
      for (Object o : element.getChildren()) {
        if (((Element)o).getName().equals(repositoryType.getName())) {
          try {
            @SuppressWarnings({"unchecked"})
            TaskRepository repository = (TaskRepository)XmlSerializer.deserialize((Element)o, repositoryType.getRepositoryClass());
            repository.setRepositoryType(repositoryType);
            repositories.add(repository);
          }
          catch (XmlSerializationException e) {
            LOG.error(e.getMessage(), e);
          }
        }
      }
    }
    return repositories;
  }

  public void projectOpened() {

    TaskProjectConfiguration projectConfiguration = getProjectConfiguration();

    servers:
    for (TaskProjectConfiguration.SharedServer server : projectConfiguration.servers) {
      if (server.type == null || server.url == null) {
        continue;
      }
      for (TaskRepositoryType<?> repositoryType : TaskRepositoryType.getRepositoryTypes()) {
        if (repositoryType.getName().equals(server.type)) {
          for (TaskRepository repository : myRepositories) {
            if (!repositoryType.equals(repository.getRepositoryType())) {
              continue;
            }
            if (server.url.equals(repository.getUrl())) {
              continue servers;
            }
          }
          TaskRepository repository = repositoryType.createRepository();
          repository.setUrl(server.url);
          repository.setShared(true);
          myRepositories.add(repository);
        }
      }
    }

    myContextManager.pack(200, 50);

    // make sure the task is associated with default changelist
    LocalTask defaultTask = findTask(LocalTaskImpl.DEFAULT_TASK_ID);
    LocalChangeList defaultList = myChangeListManager.findChangeList(LocalChangeList.DEFAULT_NAME);
    if (defaultList != null && defaultTask != null) {
      ChangeListInfo listInfo = new ChangeListInfo(defaultList);
      if (!defaultTask.getChangeLists().contains(listInfo)) {
        defaultTask.addChangelist(listInfo);
      }
    }

    // remove already not existing changelists from tasks changelists
    for (LocalTask localTask : getLocalTasks()) {
      for (Iterator<ChangeListInfo> iterator = localTask.getChangeLists().iterator(); iterator.hasNext(); ) {
        final ChangeListInfo changeListInfo = iterator.next();
        if (myChangeListManager.getChangeList(changeListInfo.id) == null) {
          iterator.remove();
        }
      }
    }

    myChangeListManager.addChangeListListener(myChangeListListener);
  }

  private TaskProjectConfiguration getProjectConfiguration() {
    return ServiceManager.getService(myProject, TaskProjectConfiguration.class);
  }

  @NotNull
  public String getComponentName() {
    return "Task Manager";
  }

  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myCacheRefreshTimer = UIUtil.createNamedTimer("TaskManager refresh", myConfig.updateInterval * 60 * 1000, new ActionListener() {
        public void actionPerformed(@NotNull ActionEvent e) {
          if (myConfig.updateEnabled && !myUpdating) {
            LOG.debug("Updating issues cache (every " + myConfig.updateInterval + " min)");
            updateIssues(null);
          }
        }
      });
      myCacheRefreshTimer.setInitialDelay(0);
      StartupManager.getInstance(myProject).registerPostStartupActivity(() -> myCacheRefreshTimer.start());
    }

    // make sure that the default task is exist
    LocalTask defaultTask = findTask(LocalTaskImpl.DEFAULT_TASK_ID);
    if (defaultTask == null) {
      defaultTask = createDefaultTask();
      addTask(defaultTask);
    }

    // search for active task
    LocalTask activeTask = null;
    final List<LocalTask> tasks = getLocalTasks();
    Collections.sort(tasks, TASK_UPDATE_COMPARATOR);
    for (LocalTask task : tasks) {
      if (activeTask == null) {
        if (task.isActive()) {
          activeTask = task;
        }
      }
      else {
        task.setActive(false);
      }
    }
    if (activeTask == null) {
      activeTask = defaultTask;
    }

    myActiveTask = activeTask;
    doActivate(myActiveTask, false);
    myDispatcher.getMulticaster().taskActivated(myActiveTask);
  }

  private static LocalTaskImpl createDefaultTask() {
    LocalTaskImpl task = new LocalTaskImpl(LocalTaskImpl.DEFAULT_TASK_ID, "Default task");
    Date date = new Date();
    task.setCreated(date);
    task.setUpdated(date);
    return task;
  }

  public void disposeComponent() {
    if (myCacheRefreshTimer != null) {
      myCacheRefreshTimer.stop();
    }
    myChangeListManager.removeChangeListListener(myChangeListListener);
  }

  public void updateIssues(final @Nullable Runnable onComplete) {
    TaskRepository first = ContainerUtil.find(getAllRepositories(), repository -> repository.isConfigured());
    if (first == null) {
      myIssueCache.clear();
      if (onComplete != null) {
        onComplete.run();
      }
      return;
    }
    myUpdating = true;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doUpdate(onComplete);
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(() -> doUpdate(onComplete));
    }
  }

  private void doUpdate(@Nullable Runnable onComplete) {
    try {
      List<Task> issues = getIssuesFromRepositories(null, 0, myConfig.updateIssuesCount, false, false, new EmptyProgressIndicator());
      if (issues == null) return;

      synchronized (myIssueCache) {
        myIssueCache.clear();
        for (Task issue : issues) {
          myIssueCache.put(issue.getId(), issue);
        }
      }
      // update local tasks
      synchronized (myTasks) {
        for (Map.Entry<String, LocalTask> entry : myTasks.entrySet()) {
          Task issue = myIssueCache.get(entry.getKey());
          if (issue != null) {
            entry.getValue().updateFromIssue(issue);
          }
        }
      }
    }
    finally {
      if (onComplete != null) {
        onComplete.run();
      }
      myUpdating = false;
    }
  }

  @Nullable
  private List<Task> getIssuesFromRepositories(@Nullable String request,
                                               int offset,
                                               int limit,
                                               boolean withClosed,
                                               boolean forceRequest,
                                               @NotNull final ProgressIndicator cancelled) {
    List<Task> issues = null;
    for (final TaskRepository repository : getAllRepositories()) {
      if (!repository.isConfigured() || (!forceRequest && myBadRepositories.contains(repository))) {
        continue;
      }
      try {
        long start = System.currentTimeMillis();
        Task[] tasks = repository.getIssues(request, offset, limit, withClosed, cancelled);
        long timeSpent = System.currentTimeMillis() - start;
        LOG.debug(String.format("Total %s ms to download %d issues from '%s' (pattern '%s')",
                               timeSpent, tasks.length, repository.getUrl(), request));
        myBadRepositories.remove(repository);
        if (issues == null) issues = new ArrayList<>(tasks.length);
        if (!repository.isSupported(TaskRepository.NATIVE_SEARCH) && request != null) {
          List<Task> filteredTasks = TaskUtil.filterTasks(request, ContainerUtil.list(tasks));
          ContainerUtil.addAll(issues, filteredTasks);
        }
        else {
          ContainerUtil.addAll(issues, tasks);
        }
      }
      catch (ProcessCanceledException ignored) {
        // OK
      }
      catch (Exception e) {
        String reason = "";
        // Fix to IDEA-111810
        //noinspection InstanceofCatchParameter
        if (e.getClass() == Exception.class || e instanceof RequestFailedException) {
          // probably contains some message meaningful to end-user
          reason = e.getMessage();
        }
        //noinspection InstanceofCatchParameter
        if (e instanceof SocketTimeoutException || e instanceof HttpRequests.HttpStatusException) {
          LOG.warn("Can't connect to " + repository + ": " + e.getMessage());
        }
        else {
          LOG.warn("Cannot connect to " + repository, e);
        }
        myBadRepositories.add(repository);
        if (forceRequest) {
          throw new RequestFailedException(repository, reason);
        }
      }
    }
    return issues;
  }

  @Override
  public boolean isVcsEnabled() {
    return ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss().length > 0;
  }

  @Override
  public AbstractVcs getActiveVcs() {
    AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    if (vcss.length == 0) return null;
    for (AbstractVcs vcs : vcss) {
      if (vcs.getType() == VcsType.distributed) {
        return vcs;
      }
    }
    return vcss[0];
  }

  @Override
  public boolean isLocallyClosed(@NotNull LocalTask localTask) {
    if (isVcsEnabled()) {
      List<ChangeListInfo> lists = localTask.getChangeLists();
      if (lists.isEmpty()) return true;
      for (ChangeListInfo list : lists) {
        if (StringUtil.isEmpty(list.id)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  @Override
  public LocalTask getAssociatedTask(@NotNull LocalChangeList list) {
    for (LocalTask task : getLocalTasks()) {
      for (ChangeListInfo changeListInfo : new ArrayList<>(task.getChangeLists())) {
        if (changeListInfo.id.equals(list.getId())) {
          return task;
        }
      }
    }
    return null;
  }

  @Override
  public void trackContext(@NotNull LocalChangeList changeList) {
    ChangeListInfo changeListInfo = new ChangeListInfo(changeList);
    String changeListName = changeList.getName();
    LocalTaskImpl task = createLocalTask(changeListName);
    task.addChangelist(changeListInfo);
    addTask(task);
    if (changeList.isDefault()) {
      activateTask(task, false);
    }
  }

  public void decorateChangeList(@NotNull LocalChangeList changeList,
                                 @NotNull ColoredTreeCellRenderer cellRenderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    LocalTask task = getAssociatedTask(changeList);
    if (task != null && task.isIssue()) {
      cellRenderer.setIcon(task.getIcon());
    }
  }

  public void createChangeList(@NotNull LocalTask task, String name) {
    String comment = TaskUtil.getChangeListComment(task);
    createChangeList(task, name, comment);
  }

  private void createChangeList(LocalTask task, String name, @Nullable String comment) {
    LocalChangeList changeList = myChangeListManager.findChangeList(name);
    if (changeList == null) {
      changeList = myChangeListManager.addChangeList(name, comment);
    }
    else {
      final LocalTask associatedTask = getAssociatedTask(changeList);
      if (associatedTask != null) {
        associatedTask.removeChangelist(new ChangeListInfo(changeList));
      }
      myChangeListManager.editComment(name, comment);
    }
    task.addChangelist(new ChangeListInfo(changeList));
    myChangeListManager.setDefaultChangeList(changeList);
  }

  public String getChangelistName(Task task) {
    String name = task.isIssue() && myConfig.changelistNameFormat != null
                  ? TaskUtil.formatTask(task, myConfig.changelistNameFormat, false)
                  : task.getSummary();
    return StringUtil.shortenTextWithEllipsis(name, 100, 0);
  }

  @NotNull
  public String suggestBranchName(@NotNull Task task) {
    String name = constructDefaultBranchName(task);
    if (task.isIssue()) return name.replace(' ', '-');
    List<String> words = StringUtil.getWordsIn(name);
    String[] strings = ArrayUtil.toStringArray(words);
    return StringUtil.join(strings, 0, Math.min(2, strings.length), "-");
  }

  @NotNull
  public String constructDefaultBranchName(@NotNull Task task) {
    return task.isIssue() ? TaskUtil.formatTask(task, myConfig.branchNameFormat, false) : task.getSummary();
  }

  @TestOnly
  public ChangeListAdapter getChangeListListener() {
    return myChangeListListener;
  }


  public static class Config {

    @Property(surroundWithTag = false)
    @XCollection(elementName = "task")
    public List<LocalTaskImpl> tasks = new ArrayList<>();

    public int localTasksCounter = 1;

    public int taskHistoryLength = 50;

    public boolean updateEnabled = true;
    public int updateInterval = 20;
    public int updateIssuesCount = 100;

    // create task options
    public boolean clearContext = true;
    public boolean createChangelist = true;
    public boolean createBranch = true;
    public boolean useBranch = false;
    public boolean shelveChanges = false;

    // close task options
    public boolean commitChanges = true;
    public boolean mergeBranch = true;

    public boolean saveContextOnCommit = true;
    public boolean trackContextForNewChangelist = false;

    public String changelistNameFormat = "{id} {summary}";
    public String branchNameFormat = "{id}";

    public boolean searchClosedTasks = false;
    @Tag("servers")
    public Element servers = new Element("servers");
  }

  private abstract class TestConnectionTask extends com.intellij.openapi.progress.Task.Modal {

    protected Exception myException;

    @Nullable
    protected TaskRepository.CancellableConnection myConnection;

    public TestConnectionTask(String title) {
      super(TaskManagerImpl.this.myProject, title, true);
    }

    @Override
    public void onCancel() {
      if (myConnection != null) {
        myConnection.cancel();
      }
    }
  }
}
