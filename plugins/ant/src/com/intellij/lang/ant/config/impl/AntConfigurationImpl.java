// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ValueProperty;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@State(name = "AntConfiguration", storages = @Storage("ant.xml"), useLoadedStateAsExisting = false)
public final class AntConfigurationImpl extends AntConfigurationBase implements PersistentStateComponent<Element>, Disposable {
  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<>("defaultAnt", AntReference.BUNDLED_ANT);
  private static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<@Nls String>() {
    @Override
    public String getName() {
      return "$defaultJDKName";
    }

    @Override
    public @Nullable String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Override
    public @Nullable String get(@NotNull AbstractPropertyContainer container) {
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
  private static final @NonNls String BUILD_FILE = "buildFile";
  private static final @NonNls String CONTEXT_MAPPING = "contextMapping";
  private static final @NonNls String CONTEXT = "context";
  private static final @NonNls String URL = "url";
  private static final @NonNls String EXECUTE_ON_ELEMENT = "executeOn";
  private static final @NonNls String EVENT_ELEMENT = "event";
  private static final @NonNls String TARGET_ELEMENT = "target";

  private final PsiManager myPsiManager;
  private final List<AntBuildFileBase> myBuildFiles = new CopyOnWriteArrayList<>();
  private final AtomicReference<List<Pair<Element, String>>> myBuildFilesConfiguration = new AtomicReference<>();

  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap = Collections.synchronizedMap(new HashMap<>());
  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = Collections.synchronizedMap(new HashMap<>());
  private final Map<VirtualFile, VirtualFile> myAntFileToContextFileMap = Collections.synchronizedMap(new HashMap<>());
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);

  public AntConfigurationImpl(final Project project) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myPsiManager = PsiManager.getInstance(project);
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
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          if (project.isDisposed()) return;

          DaemonCodeAnalyzer.getInstance(project).restart();
        });
      }
    });

    VirtualFileManager.getInstance().addAsyncFileListener(new AsyncFileListener() {
      private final ChangeApplier NO_OP = new ChangeApplier() {};

      @Override
      public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
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
    ReadAction.run(() -> {
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
    try {
      List<Pair<Element, String>> files = new ArrayList<>();
      for (Iterator<Element> iterator = state.getChildren(BUILD_FILE).iterator(); iterator.hasNext(); ) {
        Element element = iterator.next();
        iterator.remove();
        String url = element.getAttributeValue(URL);
        if (url != null) {
          files.add(Pair.create(element, url));
        }
      }
      myBuildFilesConfiguration.set(files);

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
    }
    finally {
      queueInitialization();
    }
  }

  @Override
  public void noStateLoaded() {
    myInitialized = true;
  }

  private void queueInitialization() {
    StartupManager.getInstance(getProject()).runAfterOpened(() -> {
      queueLater(new Task.Backgroundable(getProject(), AntBundle.message("progress.text.loading.ant.config"), false) {
        @Override
        public void run(final @NotNull ProgressIndicator indicator) {
          Project project = getProject();
          if (project == null || project.isDisposed()) {
            myInitialized = true; // ensure all clients waiting on isInitialized() are released
            return;
          }
          List<Pair<Element, String>> configFiles = myBuildFilesConfiguration.getAndSet(null);
          if (configFiles != null) {
            applyConfigFiles(indicator, configFiles);
          }
        }
      });
    });
  }

  // Always called from BG thread, synchronized to ensure only one initialization process at a time
  private synchronized void applyConfigFiles(@NotNull ProgressIndicator indicator, List<Pair<Element, String>> configFiles) {
    indicator.setIndeterminate(true);
    indicator.pushState();
    try {
      indicator.setText(AntBundle.message("progress.text.loading.ant.config"));
      myInitThread = Thread.currentThread();

      // first, remove existing files
      // then fill the configuration with the files configured in xml
      // updating properties separately to avoid unnecessary building of PSI after clearing caches
      runNonBlocking(() -> {
        for (AntBuildFile file : myBuildFiles) {
          removeBuildFileImpl(file);
        }
      });

      // then fill the configuration with the files configured in xml
      final VirtualFileManager vfManager = VirtualFileManager.getInstance();
      List<Pair<Element, AntBuildFileBase>> buildFiles = new ArrayList<>(configFiles.size());
      for (Pair<Element, String> elemUrl : configFiles) {
        Pair<Element, AntBuildFileBase> bf = runNonBlocking(() -> {
          final Element element = elemUrl.getFirst();
          final VirtualFile file = vfManager.findFileByUrl(elemUrl.getSecond());
          if (file != null) {
            try {
              return Pair.create(element, addBuildFileImpl(file, element));
            }
            catch (AntNoFileException ignored) {
            }
            catch (InvalidDataException e) {
              LOG.error(e);
            }
          }
          return null;
        }, null);
        if (bf != null) {
          buildFiles.add(bf);
        }
      }

      // updating properties separately to avoid unnecessary building of PSI after clearing caches
      for (Pair<Element, AntBuildFileBase> elemBuildFile : buildFiles) {
        runNonBlocking(() -> {
          final AntBuildFileBase buildFile = elemBuildFile.getSecond();
          buildFile.updateProperties();
          for (Element e : elemBuildFile.getFirst().getChildren(EXECUTE_ON_ELEMENT)) {
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
        });
      }
      ReadAction.run(() -> {
        try {
          AntWorkspaceConfiguration.getInstance(getProject()).loadFileProperties();
        }
        catch (InvalidDataException e) {
          LOG.error(e);
        }
        finally {
          incModificationCount();
          updateRegisteredActions();
        }
      });
    }
    finally {
      myInitThread = null;
      LOG.info("AntConfiguration: initialized");
      myInitialized = true;
      ApplicationManager.getApplication().invokeLater(
        () -> myEventDispatcher.getMulticaster().configurationLoaded(),
        ModalityState.any()
      );
      indicator.popState();
    }
  }

  @Override
  public void ensureInitialized() {
    if (!isInitialized()) {
      ProgressIndicatorUtils.awaitWithCheckCanceled(() -> {
        if (!isInitialized()) {
          TimeoutUtil.sleep(10);
          return isInitialized();
        }
        return true;
      });
    }
  }

  private volatile boolean myInitialized = false;
  private volatile Thread myInitThread;

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public boolean hasBuildFiles() {
    if (!myBuildFiles.isEmpty() || !myInitialized) {
      return true;
    }
    List<Pair<Element, String>> files = myBuildFilesConfiguration.get();
    return files != null && !files.isEmpty();
  }

  @Override
  public AntBuildFile[] getBuildFiles() {
    return myBuildFiles.toArray(new AntBuildFileBase[0]);
  }

  @Override
  public List<AntBuildFileBase> getBuildFileList() {
    return myBuildFiles;
  }

  @Override
  public @Nullable AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    final Ref<AntBuildFile> result = Ref.create(null);
    final Ref<AntNoFileException> ex = Ref.create(null);
    final String title = AntBundle.message("dialog.title.register.ant.build.file", file.getPresentableUrl());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), title, false) {
      @Override
      public @NotNull NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Ant", AntBundle.message("system.notification.title.ant.task.finished"), "");
      }

      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(AntBundle.message("progress.text.register.ant.build.file", file.getPresentableUrl()));
          incModificationCount();
          boolean added = runNonBlocking(() -> {
            try {
              for (AntBuildFile buildFile : getBuildFileList()) {
                final VirtualFile vFile = buildFile.getVirtualFile();
                if (vFile != null && vFile.equals(file)) {
                  result.set(buildFile);
                  return Boolean.FALSE;
                }
              }
              result.set(addBuildFileImpl(file, null));
              updateRegisteredActions();
              return Boolean.TRUE;
            }
            catch (AntNoFileException e) {
              ex.set(e);
            }
            return Boolean.FALSE;
          }, Boolean.FALSE);
          if (added) {
            ApplicationManager.getApplication().invokeLater(() -> myEventDispatcher.getMulticaster().buildFileAdded(result.get()), ModalityState.any());
          }
        }
        catch (ProcessCanceledException ignored) {
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

  private boolean removeBuildFiles(Collection<AntBuildFileBase> files) {
    boolean removed = false;
    for (AntBuildFileBase file : files) {
      incModificationCount();
      removed |= removeBuildFileImpl(file);
    }
    updateRegisteredActions();
    return removed;
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
    if (events.isEmpty()) {
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
  public @Nullable AntBuildTarget getTargetForEvent(final ExecutionEvent event) {
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
    for (ExecutionEvent ev : getEventsByClass()) {
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
  public @Nullable AntBuildModelBase getModelIfRegistered(@NotNull AntBuildFileBase buildFile) {
    return myBuildFiles.contains(buildFile) ? getModel(buildFile) : null;
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
  public @Nullable AntBuildFile findBuildFileByActionId(final String id) {
    for (AntBuildFile buildFile : myBuildFiles) {
      AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      if (id.equals(model.getDefaultTargetActionId())) {
        return buildFile;
      }
      if (model.hasTargetWithActionId(id)) return buildFile;
    }
    return null;
  }

  private AntBuildFileBase addBuildFileImpl(final VirtualFile file, @Nullable Element element) throws AntNoFileException {
    PsiFile xmlFile = myPsiManager.findFile(file);
    if (!(xmlFile instanceof XmlFile)) {
      throw new AntNoFileException(AntBundle.message("ant.cannot.add.build.file.reason.file.is.not.xml"), file);
    }
    AntSupport.markFileAsAntFile(file, xmlFile.getProject(), true);
    if (!AntDomFileDescription.isAntFile(((XmlFile)xmlFile))) {
      throw new AntNoFileException(AntBundle.message("ant.cannot.add.build.file.reason.file.not.ant.file"), file);
    }
    final AntBuildFileImpl buildFile = new AntBuildFileImpl(xmlFile, this);
    if (element != null) {
      buildFile.readProperties(element);
    }
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
      for (String oldId : actionManager.getActionIdList(AntConfiguration.getActionIdPrefix(project))) {
        actionManager.unregisterAction(oldId);
      }
      if (!actionList.isEmpty()) {
        final Set<String> registeredIds = new HashSet<>();
        for (Pair<String, AnAction> pair : actionList) {
          if (!registeredIds.contains(pair.first)) {
            registeredIds.add(pair.first);
            actionManager.registerAction(pair.first, pair.second);
          }
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

  private boolean removeBuildFileImpl(@NotNull AntBuildFile buildFile) {
    XmlFile antFile = buildFile.getAntFile();
    if (antFile != null) {
      AntSupport.markFileAsAntFile(antFile.getOriginalFile().getVirtualFile(), antFile.getProject(), false);
    }

    boolean removed = myBuildFiles.remove(buildFile);
    removed |= myModelToBuildFileMap.remove(buildFile) != null;

    if (removed) {
      myEventDispatcher.getMulticaster().buildFileRemoved(buildFile);
    }
    return removed;
  }

  @Override
  public boolean executeTargetBeforeCompile(final CompileContext compileContext, final DataContext dataContext) {
    return runTargetSynchronously(compileContext, dataContext, ExecuteBeforeCompilationEvent.getInstance());
  }

  @Override
  public boolean executeTargetAfterCompile(final CompileContext compileContext, final DataContext dataContext) {
    return runTargetSynchronously(compileContext, dataContext, ExecuteAfterCompilationEvent.getInstance());
  }

  private boolean runTargetSynchronously(CompileContext compileContext, final DataContext dataContext, ExecutionEvent event) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    final ProgressIndicator progress = compileContext.getProgressIndicator();
    progress.pushState();
    try {
      if (!isInitialized()) {
        progress.setText(AntBundle.message("progress.text.loading.ant.config"));
        ensureInitialized();
      }

      final AntBuildTarget target = getTargetForEvent(event);
      if (target == null) {
        // no task assigned
        return true;
      }

      if (ExecuteAfterCompilationEvent.TYPE_ID.equals(event.getTypeId()) && compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        compileContext.addMessage(
          CompilerMessageCategory.INFORMATION, AntBundle.message("message.skip.ant.target.after.compilation.errors", target.getDisplayName()), null, -1, -1
        );
        return true;
      }

      progress.setText(AntBundle.message("progress.text.running.ant.tasks"));
      return executeTargetSynchronously(dataContext, target);
    }
    finally {
      progress.popState();
    }
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
    if (task.isHeadless()) {
      // for headless tasks we need to ensure async execution.
      // Otherwise, calls to AntConfiguration.getInstance() from the task will cause SOE
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
  public @Nullable XmlFile getContextFile(final @Nullable XmlFile file) {
    if (file == null) {
      return null;
    }
    final VirtualFile context = myAntFileToContextFileMap.get(file.getVirtualFile());
    if (context == null) {
      return null;
    }
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(context);
    if (!(psiFile instanceof XmlFile xmlFile)) {
      return null;
    }
    return AntDomFileDescription.isAntFile(xmlFile)? xmlFile : null;
  }

  @Override
  public @Nullable AntBuildFileBase getAntBuildFile(@NotNull PsiFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      for (AntBuildFileBase bFile : myBuildFiles) {
        if (vFile.equals(bFile.getVirtualFile())) {
          return bFile;
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable XmlFile getEffectiveContextFile(final XmlFile file) {
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
      ApplicationManager.getApplication().executeOnPooledThread(() -> runNonBlocking(() -> removeBuildFiles(myAntFiles)));
    }
  }

  private void runNonBlocking(@NotNull Runnable task) {
    runNonBlocking(() -> {
      task.run();
      return null;
    }, null);
  }

  private <T> T runNonBlocking(@NotNull Callable<? extends T> task, T defValue) {
    try {
      return ReadAction.nonBlocking(task).expireWith(this).executeSynchronously();
    }
    catch (ProcessCanceledException ex) {
      return defValue;
    }
  }
}
