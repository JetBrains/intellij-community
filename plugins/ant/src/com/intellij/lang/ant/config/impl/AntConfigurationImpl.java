// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.EventDispatcher;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ValueProperty;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@State(name = "AntConfiguration", storages = @Storage("ant.xml"), useLoadedStateAsExisting = false)
public class AntConfigurationImpl extends AntConfigurationBase implements PersistentStateComponent<Element>, Disposable {
  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<>("defaultAnt", AntReference.BUNDLED_ANT);
  private static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<String>() {
    @Override
    public String getName() {
      return "$defaultJDKName";
    }

    @Override
    @Nullable
    public String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Override
    @Nullable
    public String get(@NotNull AbstractPropertyContainer container) {
      if (!container.hasProperty(this)) {
        return null;
      }

      AntConfiguration antConfiguration = INSTANCE.get(container);
      return ProjectRootManager.getInstance(antConfiguration.getProject()).getProjectSdkName();
    }

    @Override
    public String copy(final String jdkName) {
      return jdkName;
    }
  };

  private static final Logger LOG = Logger.getInstance(AntConfigurationImpl.class);
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String CONTEXT_MAPPING = "contextMapping";
  @NonNls private static final String CONTEXT = "context";
  @NonNls private static final String URL = "url";
  @NonNls private static final String EXECUTE_ON_ELEMENT = "executeOn";
  @NonNls private static final String EVENT_ELEMENT = "event";
  @NonNls private static final String TARGET_ELEMENT = "target";

  private final PsiManager myPsiManager;
  private final List<AntBuildFileBase> myBuildFiles = new CopyOnWriteArrayList<>();
  private volatile List<Pair<Element, String>> myBuildFilesConfiguration = Collections.emptyList();

  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap = Collections.synchronizedMap(new HashMap<>());
  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = Collections.synchronizedMap(new HashMap<>());
  private final Map<VirtualFile, VirtualFile> myAntFileToContextFileMap = Collections.synchronizedMap(new HashMap<>());
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);
  private final StartupManager myStartupManager;

  public AntConfigurationImpl(final Project project) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myPsiManager = PsiManager.getInstance(project);
    myStartupManager = StartupManager.getInstance(project);
    addAntConfigurationListener(new AntConfigurationListener() {
      @Override
      public void configurationLoaded() {
        restartDaemon();
      }
      @Override
      public void buildFileChanged(final AntBuildFile buildFile) {
        restartDaemon();
      }
      @Override
      public void buildFileAdded(final AntBuildFile buildFile) {
        restartDaemon();
      }
      @Override
      public void buildFileRemoved(final AntBuildFile buildFile) {
        restartDaemon();
      }
      private void restartDaemon() {
        GuiUtils.invokeLaterIfNeeded(() -> DaemonCodeAnalyzer.getInstance(project).restart(), ModalityState.any());
      }
    });

    VirtualFileManager.getInstance().addAsyncFileListener(new AsyncFileListener() {
      private final ChangeApplier NO_OP = new ChangeApplier() {};

      @Nullable
      @Override
      public ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        Set<VirtualFile> toDelete = null;
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            if (toDelete == null) {
              toDelete = new HashSet<>();
            }
            toDelete.add(event.getFile());
          }
        }
        if (toDelete == null) {
          return NO_OP;
        }
        Set<AntBuildFileBase> antFiles = null;
        for (AntBuildFileBase file : myBuildFiles) {
          if (toDelete.contains(file.getVirtualFile())) {
            if (antFiles == null) {
              antFiles = new HashSet<>();
            }
            antFiles.add(file);
          }
        }
        return antFiles == null? NO_OP : new FileDeletionChangeApplier(toDelete, antFiles);
      }
    }, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public Element getState() {
    final Element state = new Element("state");
    getProperties().writeExternal(state);
    ApplicationManager.getApplication().runReadAction(() -> {
      for (final AntBuildFileBase buildFile : myBuildFiles) {
        final Element element = new Element(BUILD_FILE);
        //noinspection ConstantConditions
        element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
        buildFile.writeProperties(element);
        saveEvents(element, buildFile);
        state.addContent(element);
      }

      final List<VirtualFile> files;
      synchronized (myAntFileToContextFileMap) {
        files = new ArrayList<>(myAntFileToContextFileMap.keySet());
      }
      // sort in order to minimize changes
      files.sort(Comparator.comparing(VirtualFile::getUrl));
      for (VirtualFile file : files) {
        final Element element = new Element(CONTEXT_MAPPING);
        element.setAttribute(URL, file.getUrl());
        final VirtualFile contextFile = myAntFileToContextFileMap.get(file);
        if (contextFile != null) {
          element.setAttribute(CONTEXT, contextFile.getUrl());
        }
        state.addContent(element);
      }
    });
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    List<Pair<Element, String>> files = new ArrayList<>();
    for (Iterator<Element> iterator = state.getChildren(BUILD_FILE).iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      iterator.remove();
      String url = element.getAttributeValue(URL);
      if (url != null) {
        files.add(Pair.create(element, url));
      }
    }
    myBuildFilesConfiguration = files;

    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    // contexts
    myAntFileToContextFileMap.clear();
    for (Element element : state.getChildren(CONTEXT_MAPPING)) {
      String url = element.getAttributeValue(URL);
      String contextUrl = element.getAttributeValue(CONTEXT);
      assert url != null;
      VirtualFile file = vfManager.findFileByUrl(url);
      assert contextUrl != null;
      VirtualFile contextFile = vfManager.findFileByUrl(contextUrl);
      if (file != null && contextFile != null) {
        myAntFileToContextFileMap.put(file, contextFile);
      }
    }

    getProperties().readExternal(state);

    myInitializationState.set(InitializationState.IN_PROGRESS);
    queueInitialization();
  }

  @Override
  public void noStateLoaded() {
    myInitializationState.compareAndSet(InitializationState.NOT_LOADED, InitializationState.INITIALIZED);
  }

  private void queueInitialization() {
    try {
      runWhenInitialized(() -> {
        String title = AntBundle.message("loading.ant.config.progress");
        queueLater(new Task.Backgroundable(getProject(), title, false) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            if (getProject().isDisposed()) {
              return;
            }

            indicator.setIndeterminate(true);
            indicator.pushState();
            try {
              indicator.setText(title);
              ApplicationManager.getApplication().runReadAction(() -> {
                try {
                  myInitThread = Thread.currentThread();
                  // first, remove existing files
                  for (AntBuildFile file : myBuildFiles) {
                    removeBuildFileImpl(file);
                  }
                  myBuildFiles.clear();

                  // then fill the configuration with the files configured in xml
                  final VirtualFileManager vfManager = VirtualFileManager.getInstance();
                  List<Pair<Element, String>> files = myBuildFilesConfiguration;
                  List<Pair<Element, AntBuildFileBase>> buildFiles = new ArrayList<>(files.size());
                  for (Pair<Element, String> pair : files) {
                    final Element element = pair.getFirst();
                    final VirtualFile file = vfManager.findFileByUrl(pair.getSecond());
                    if (file == null) {
                      continue;
                    }
                    try {
                      final AntBuildFileBase buildFile = addBuildFileImpl(file);
                      buildFile.readProperties(element);
                      buildFiles.add(Pair.create(element, buildFile));
                    }
                    catch (AntNoFileException ignored) {
                    }
                    catch (InvalidDataException e) {
                      LOG.error(e);
                    }
                  }

                  // updating properties separately to avoid unnecessary building of PSI after clearing caches
                  for (Pair<Element, AntBuildFileBase> pair : buildFiles) {
                    final AntBuildFileBase buildFile = pair.getSecond();
                    buildFile.updateProperties();
                    for (Element e : pair.getFirst().getChildren(EXECUTE_ON_ELEMENT)) {
                      final String eventId = e.getAttributeValue(EVENT_ELEMENT);
                      ExecutionEvent event = null;
                      final String targetName = e.getAttributeValue(TARGET_ELEMENT);
                      if (ExecuteBeforeCompilationEvent.TYPE_ID.equals(eventId)) {
                        event = ExecuteBeforeCompilationEvent.getInstance();
                      }
                      else if (ExecuteAfterCompilationEvent.TYPE_ID.equals(eventId)) {
                        event = ExecuteAfterCompilationEvent.getInstance();
                      }
                      else if (ExecuteCompositeTargetEvent.TYPE_ID.equals(eventId)) {
                        try {
                          event = new ExecuteCompositeTargetEvent(targetName);
                        }
                        catch (WrongNameFormatException e1) {
                          LOG.info(e1);
                          event = null;
                        }
                      }
                      if (event != null) {
                        try {
                          event.readExternal(e, getProject());
                          setTargetForEvent(buildFile, targetName, event);
                        }
                        catch (InvalidDataException readFailed) {
                          LOG.info(readFailed.getMessage());
                        }
                      }
                    }
                  }
                  AntWorkspaceConfiguration.getInstance(getProject()).loadFileProperties();
                }
                catch (InvalidDataException e) {
                  LOG.error(e);
                }
                finally {
                  try {
                    incModificationCount();
                    updateRegisteredActions();
                  }
                  finally {
                    myInitThread = null;
                    LOG.info("queueInitialization: initialized");
                    myInitializationState.set(InitializationState.INITIALIZED);
                    myBuildFilesConfiguration = Collections.emptyList();
                    ApplicationManager.getApplication().invokeLater(() -> myEventDispatcher.getMulticaster().configurationLoaded(), ModalityState.any());
                  }
                }
              });
            }
            finally {
              indicator.popState();
            }
          }
        });
      });
    }
    catch (Throwable t) {
      myInitializationState.set(InitializationState.FAILED_TO_INITIALIZE);
      throw t;
    }
  }

  @Override
  public void ensureInitialized() {
    if (myInitializationState.compareAndSet(InitializationState.FAILED_TO_INITIALIZE, InitializationState.IN_PROGRESS)) {
      queueInitialization();
    }

    int attemptCount = 0; // need this in order to make sure we will not block swing thread forever
    while (!isInitialized() && attemptCount < 6000) {
      TimeoutUtil.sleep(10);
      attemptCount++;
    }
  }

  private enum InitializationState { NOT_LOADED, IN_PROGRESS, FAILED_TO_INITIALIZE, INITIALIZED}
  private final AtomicReference<InitializationState> myInitializationState = new AtomicReference<>(InitializationState.NOT_LOADED);
  private volatile Thread myInitThread;

  @Override
  public boolean isInitialized() {
    return myInitializationState.get() == InitializationState.INITIALIZED;
  }

  @Override
  public AntBuildFile[] getBuildFiles() {
    return myBuildFiles.toArray(new AntBuildFileBase[0]);
  }

  @Override
  public List<AntBuildFileBase> getBuildFileList() {
    return myBuildFiles;
  }

  @Nullable
  @Override
  public AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    final Ref<AntBuildFile> result = Ref.create(null);
    final Ref<AntNoFileException> ex = Ref.create(null);
    final String title = AntBundle.message("register.ant.build.progress", file.getPresentableUrl());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), title, false) {
      @NotNull
      @Override
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Ant", "Ant Task Finished", "");
      }

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          incModificationCount();
          boolean added = ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
            try {
              for (AntBuildFile buildFile : getBuildFileList()) {
                final VirtualFile vFile = buildFile.getVirtualFile();
                if (vFile != null && vFile.equals(file)) {
                  result.set(buildFile);
                  return Boolean.FALSE;
                }
              }
              result.set(addBuildFileImpl(file));
              updateRegisteredActions();
              return Boolean.TRUE;
            }
            catch (AntNoFileException e) {
              ex.set(e);
            }
            return Boolean.FALSE;
          });
          if (added) {
            ApplicationManager.getApplication().invokeLater(() -> myEventDispatcher.getMulticaster().buildFileAdded(result.get()));
          }
        }
        finally {
          indicator.popState();
        }
      }
    });
    if (ex.get() != null) {
      throw ex.get();
    }
    return result.get();
  }

  @Override
  public void removeBuildFile(@NotNull AntBuildFile file) {
    removeBuildFiles(Collections.singleton((AntBuildFileBase)file));
  }

  private void removeBuildFiles(Collection<AntBuildFileBase> files) {
    for (AntBuildFileBase file : files) {
      incModificationCount();
      removeBuildFileImpl(file);
    }
    myBuildFiles.removeAll(files);
    updateRegisteredActions();
  }

  @Override
  public void addAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void removeAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  @Override
  public boolean isFilterTargets() {
    return getAntWorkspaceConfiguration().FILTER_TARGETS;
  }

  @Override
  public void setFilterTargets(final boolean value) {
    getAntWorkspaceConfiguration().FILTER_TARGETS = value;
  }

  @Override
  public AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile) {
    final List<ExecutionEvent> events = getEventsByClass();
    if (events.size() == 0) {
      return AntBuildTargetBase.EMPTY_ARRAY;
    }
    return events.stream().map(event -> (MetaTarget)getTargetForEvent(event))
      .filter(target -> target != null && buildFile.equals(target.getBuildFile())).toArray(AntBuildTarget[]::new);
  }

  @Override
  public List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target) {
    final List<ExecutionEvent> list = new ArrayList<>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final AntBuildTarget targetForEvent = getTargetForEvent(event);
        if (target.equals(targetForEvent)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  @Override
  @Nullable
  public AntBuildTarget getTargetForEvent(final ExecutionEvent event) {
    final Pair<AntBuildFile, String> pair = myEventToTargetMap.get(event);
    if (pair == null) {
      return null;
    }
    final AntBuildFileBase buildFile = (AntBuildFileBase)pair.first;
    if (!myBuildFiles.contains(buildFile)) {
      // file was removed
      return null;
    }
    final String targetName = pair.second;

    final AntBuildTarget antBuildTarget = buildFile.getModel().findTarget(targetName);
    if (antBuildTarget != null) {
      return antBuildTarget;
    }
    final List<ExecutionEvent> events = getEventsByClass();
    if (events.size() == 0) {
      return null;
    }
    for (ExecutionEvent ev : events) {
      final String name = ExecuteCompositeTargetEvent.TYPE_ID.equals(ev.getTypeId())? ((ExecuteCompositeTargetEvent)ev).getMetaTargetName() : ev.getPresentableName();
      if (Comparing.strEqual(targetName, name)) {
        return new MetaTarget(buildFile, ev.getPresentableName(), ((ExecuteCompositeTargetEvent)ev).getTargetNames());
      }
    }
    return null;
  }

  @Override
  public void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event) {
    myEventToTargetMap.put(event, Pair.create(buildFile, targetName));
  }

  @Override
  public void clearTargetForEvent(final ExecutionEvent event) {
    myEventToTargetMap.remove(event);
  }

  @Override
  public void updateBuildFile(final AntBuildFile buildFile) {
    incModificationCount();
    myEventDispatcher.getMulticaster().buildFileChanged(buildFile);
    updateRegisteredActions();
  }

  @Override
  public boolean isAutoScrollToSource() {
    return getAntWorkspaceConfiguration().IS_AUTOSCROLL_TO_SOURCE;
  }

  @Override
  public void setAutoScrollToSource(final boolean value) {
    getAntWorkspaceConfiguration().IS_AUTOSCROLL_TO_SOURCE = value;
  }

  @Override
  public AntInstallation getProjectDefaultAnt() {
    return DEFAULT_ANT.get(getProperties()).find(GlobalAntConfiguration.getInstance());
  }

  @Override
  @Nullable
  public AntBuildModelBase getModelIfRegistered(@NotNull AntBuildFileBase buildFile) {
    return myBuildFiles.contains(buildFile) ? getModel(buildFile) : null;
  }

  private void runWhenInitialized(final Runnable runnable) {
    if (getProject().isInitialized()) {
      ApplicationManager.getApplication().runReadAction(runnable);
    }
    else {
      myStartupManager.runWhenProjectIsInitialized(runnable);
    }
  }

  private void saveEvents(final Element element, final AntBuildFile buildFile) {
    List<Element> events = null;
    final Set<String> savedEvents = new HashSet<>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final Pair<AntBuildFile, String> pair = myEventToTargetMap.get(event);
        if (!buildFile.equals(pair.first)) {
          continue;
        }
        Element eventElement = new Element(EXECUTE_ON_ELEMENT);
        eventElement.setAttribute(EVENT_ELEMENT, event.getTypeId());
        eventElement.setAttribute(TARGET_ELEMENT, pair.second);

        final String id = event.writeExternal(eventElement, getProject());
        if (savedEvents.contains(id)) continue;
        savedEvents.add(id);

        if (events == null) {
          events = new ArrayList<>();
        }
        events.add(eventElement);
      }
    }

    if (events != null) {
      events.sort(EventElementComparator.INSTANCE);
      for (Element eventElement : events) {
        element.addContent(eventElement);
      }
    }
  }

  @Override
  public AntBuildModelBase getModel(@NotNull AntBuildFile buildFile) {
    AntBuildModelBase model = myModelToBuildFileMap.get(buildFile);
    if (model == null) {
      synchronized (myModelToBuildFileMap) {
        model = new AntBuildModelImpl(buildFile);
        final AntBuildModelBase prev = myModelToBuildFileMap.put(buildFile, model);
        if (prev != null) {
          model = prev;
          myModelToBuildFileMap.put(buildFile, prev);
        }
      }
    }
    return model;
  }

  @Override
  @Nullable
  public AntBuildFile findBuildFileByActionId(final String id) {
    for (AntBuildFile buildFile : myBuildFiles) {
      AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      if (id.equals(model.getDefaultTargetActionId())) {
        return buildFile;
      }
      if (model.hasTargetWithActionId(id)) return buildFile;
    }
    return null;
  }

  private AntBuildFileBase addBuildFileImpl(final VirtualFile file) throws AntNoFileException {
    PsiFile xmlFile = myPsiManager.findFile(file);
    if (!(xmlFile instanceof XmlFile)) {
      throw new AntNoFileException("the file is not an xml file", file);
    }
    AntSupport.markFileAsAntFile(file, xmlFile.getProject(), true);
    if (!AntDomFileDescription.isAntFile(((XmlFile)xmlFile))) {
      throw new AntNoFileException("the file is not recognized as an Ant file", file);
    }
    final AntBuildFileImpl buildFile = new AntBuildFileImpl((XmlFile)xmlFile, this);
    myBuildFiles.add(buildFile);
    return buildFile;
  }

  private void updateRegisteredActions() {
    final Project project = getProject();
    if (project.isDisposed()) {
      return;
    }
    final List<Pair<String, AnAction>> actionList = new ArrayList<>();
    for (final AntBuildFileBase buildFile : myBuildFiles) {
      final AntBuildModelBase model = buildFile.getModel();
      String defaultTargetActionId = model.getDefaultTargetActionId();
      if (defaultTargetActionId != null) {
        final TargetAction action = new TargetAction(
          buildFile, TargetAction.getDefaultTargetName(), Collections.singletonList(TargetAction.getDefaultTargetName()), null
        );
        actionList.add(new Pair<>(defaultTargetActionId, action));
      }

      collectTargetActions(model.getFilteredTargets(), actionList, buildFile);
      collectTargetActions(getMetaTargets(buildFile), actionList, buildFile);
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      // unregister Ant actions
      ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(project));
      for (String oldId : oldIds) {
        actionManager.unregisterAction(oldId);
      }
      final Set<String> registeredIds = new HashSet<>();
      for (Pair<String, AnAction> pair : actionList) {
        if (!registeredIds.contains(pair.first)) {
          registeredIds.add(pair.first);
          actionManager.registerAction(pair.first, pair.second);
        }
      }
    }
  }

  private AntWorkspaceConfiguration getAntWorkspaceConfiguration() {
    return AntWorkspaceConfiguration.getInstance(getProject());
  }

  private static void collectTargetActions(final AntBuildTarget[] targets,
                                           final List<? super Pair<String, AnAction>> actionList,
                                           final AntBuildFile buildFile) {
    for (final AntBuildTarget target : targets) {
      final String actionId = ((AntBuildTargetBase)target).getActionId();
      if (actionId != null) {
        final TargetAction action = new TargetAction(
          buildFile, target.getName(), target.getTargetNames(), target.getNotEmptyDescription()
        );
        actionList.add(new Pair<>(actionId, action));
      }
    }
  }

  private void removeBuildFileImpl(@NotNull AntBuildFile buildFile) {
    XmlFile antFile = buildFile.getAntFile();
    if (antFile != null) {
      AntSupport.markFileAsAntFile(antFile.getOriginalFile().getVirtualFile(), antFile.getProject(), false);
    }

    myModelToBuildFileMap.remove(buildFile);
    myEventDispatcher.getMulticaster().buildFileRemoved(buildFile);
  }

  @Override
  public boolean executeTargetBeforeCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteBeforeCompilationEvent.getInstance());
  }

  @Override
  public boolean executeTargetAfterCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteAfterCompilationEvent.getInstance());
  }

  private boolean runTargetSynchronously(final DataContext dataContext, ExecutionEvent event) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      throw new IllegalStateException("Called in the event dispatch thread");
    }
    final AntBuildTarget target = getTargetForEvent(event);
    if (target == null) {
      // no task assigned
      return true;
    }
    return executeTargetSynchronously(dataContext, target);
  }

  public static boolean executeTargetSynchronously(final DataContext dataContext, final AntBuildTarget target) {
    return executeTargetSynchronously(dataContext, target, Collections.emptyList());
  }

  public static boolean executeTargetSynchronously(final DataContext dataContext, final AntBuildTarget target, final List<BuildFileProperty> additionalProperties) {
    final Semaphore targetDone = new Semaphore();
    targetDone.down();
    final Ref<Boolean> result = Ref.create(Boolean.FALSE);

    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        final Project project = dataContext.getData(CommonDataKeys.PROJECT);
        if (project == null || project.isDisposed()) {
          targetDone.up();
        }
        else {
          target.run(dataContext, additionalProperties, new AntBuildListener() {
            @Override
            public void buildFinished(int state, int errorCount) {
              result.set((state == AntBuildListener.FINISHED_SUCCESSFULLY) && (errorCount == 0));
              targetDone.up();
            }
          });
        }
      }
      catch (Throwable e) {
        targetDone.up();
        LOG.error(e);
      }
    });
    targetDone.waitFor();
    return result.get();
  }

  private List<ExecutionEvent> getEventsByClass() {
    final Thread initThread = myInitThread;
    if (initThread == null || initThread != Thread.currentThread()) {
      ensureInitialized();
    }
    final List<ExecutionEvent> list = new ArrayList<>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        if (event instanceof ExecuteCompositeTargetEvent) {
          list.add(event);
        }
      }
    }
    return list;
  }

  private static void queueLater(final Task task) {
    final Application app = ApplicationManager.getApplication();
    if (!app.isDispatchThread() || task.isHeadless()) {
      // for headless tasks we need to ensure async execution.
      // Otherwise calls to AntConfiguration.getInstance() from the task will cause SOE
      app.invokeLater(task::queue);
    }
    else {
      task.queue();
    }
  }

  @Override
  public void setContextFile(@NotNull XmlFile file, @Nullable XmlFile context) {
    if (context != null) {
      myAntFileToContextFileMap.put(file.getVirtualFile(), context.getVirtualFile());
    }
    else {
      myAntFileToContextFileMap.remove(file.getVirtualFile());
    }
  }

  @Override
  @Nullable
  public XmlFile getContextFile(@Nullable final XmlFile file) {
    if (file == null) {
      return null;
    }
    final VirtualFile context = myAntFileToContextFileMap.get(file.getVirtualFile());
    if (context == null) {
      return null;
    }
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(context);
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final XmlFile xmlFile = (XmlFile)psiFile;
    return AntDomFileDescription.isAntFile(xmlFile)? xmlFile : null;
  }

  @Override
  @Nullable
  public AntBuildFileBase getAntBuildFile(@NotNull PsiFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      for (AntBuildFile bFile : myBuildFiles) {
        if (vFile.equals(bFile.getVirtualFile())) {
          return (AntBuildFileBase)bFile;
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public XmlFile getEffectiveContextFile(final XmlFile file) {
    return new Object() {
      @Nullable XmlFile findContext(final XmlFile file, Set<? super PsiElement> processed) {
        if (file != null) {
          processed.add(file);
          final XmlFile contextFile = getContextFile(file);
          return (contextFile == null || processed.contains(contextFile))? file : findContext(contextFile, processed);
        }
        return null;
      }
    }.findContext(file, new HashSet<>());
  }

  private static class EventElementComparator implements Comparator<Element> {
    static final Comparator<? super Element> INSTANCE = new EventElementComparator();

    private static final String[] COMPARABLE_ATTRIB_NAMES = new String[] {
      EVENT_ELEMENT,
      TARGET_ELEMENT,
      ExecuteCompositeTargetEvent.PRESENTABLE_NAME
    };

    @Override
    public int compare(final Element o1, final Element o2) {
      for (String attribName : COMPARABLE_ATTRIB_NAMES) {
        final int valuesEqual = Comparing.compare(o1.getAttributeValue(attribName), o2.getAttributeValue(attribName));
        if (valuesEqual != 0) {
          return valuesEqual;
        }
      }
      return 0;
    }
  }

  private class FileDeletionChangeApplier implements AsyncFileListener.ChangeApplier {
    private final Set<VirtualFile> myDeleted;
    private final Set<AntBuildFileBase> myAntFiles;

    FileDeletionChangeApplier(Set<VirtualFile> deleted, Set<AntBuildFileBase> antFiles) {
      myDeleted = deleted;
      myAntFiles = antFiles;
    }

    @Override
    public void beforeVfsChange() {
      synchronized (myAntFileToContextFileMap) {
        for (Iterator<Map.Entry<VirtualFile,VirtualFile>> it = myAntFileToContextFileMap.entrySet().iterator(); it.hasNext();) {
          final Map.Entry<VirtualFile, VirtualFile> entry = it.next();
          if (myDeleted.contains(entry.getKey()) || myDeleted.contains(entry.getValue())) {
            it.remove();
          }
        }
      }
    }

    @Override
    public void afterVfsChange() {
      final Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> app.runReadAction(()-> removeBuildFiles(myAntFiles)));
    }
  }
}
