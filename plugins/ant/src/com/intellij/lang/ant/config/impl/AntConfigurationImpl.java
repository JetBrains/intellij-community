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
package com.intellij.lang.ant.config.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.dom.AntDomFileDescription;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ValueProperty;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@State(name = "AntConfiguration", storages = @Storage("ant.xml"))
public class AntConfigurationImpl extends AntConfigurationBase implements PersistentStateComponent<Element> {
  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<>("defaultAnt", AntReference.BUNDLED_ANT);
  private static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<String>() {
    public String getName() {
      return "$defaultJDKName";
    }

    @Nullable
    public String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Nullable
    public String get(@NotNull AbstractPropertyContainer container) {
      if (!container.hasProperty(this)) {
        return null;
      }

      AntConfiguration antConfiguration = INSTANCE.get(container);
      return ProjectRootManager.getInstance(antConfiguration.getProject()).getProjectSdkName();
    }

    public String copy(final String jdkName) {
      return jdkName;
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntConfigurationImpl");
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String CONTEXT_MAPPING = "contextMapping";
  @NonNls private static final String CONTEXT = "context";
  @NonNls private static final String URL = "url";
  @NonNls private static final String EXECUTE_ON_ELEMENT = "executeOn";
  @NonNls private static final String EVENT_ELEMENT = "event";
  @NonNls private static final String TARGET_ELEMENT = "target";

  private final PsiManager myPsiManager;
  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap =
    new HashMap<>();

  private final List<AntBuildFileBase> myBuildFiles = new CopyOnWriteArrayList<>();

  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = new HashMap<>();
  private final Map<VirtualFile, VirtualFile> myAntFileToContextFileMap = new java.util.HashMap<>();
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);
  private final AntWorkspaceConfiguration myAntWorkspaceConfiguration;
  private final StartupManager myStartupManager;

  public AntConfigurationImpl(final Project project, final AntWorkspaceConfiguration antWorkspaceConfiguration, final DaemonCodeAnalyzer daemon) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myAntWorkspaceConfiguration = antWorkspaceConfiguration;
    myPsiManager = PsiManager.getInstance(project);
    myStartupManager = StartupManager.getInstance(project);
    addAntConfigurationListener(new AntConfigurationListener() {
      public void configurationLoaded() {
        restartDaemon();
      }
      public void buildFileChanged(final AntBuildFile buildFile) {
        restartDaemon();
      }
      public void buildFileAdded(final AntBuildFile buildFile) {
        restartDaemon();
      }
      public void buildFileRemoved(final AntBuildFile buildFile) {
        restartDaemon();
      }
      private void restartDaemon() {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          daemon.restart();
        }
        else {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(daemon::restart);
        }
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      public void beforeFileDeletion(@NotNull final VirtualFileEvent event) {
        final VirtualFile vFile = event.getFile();
        // cleanup
        for (AntBuildFile file : getBuildFiles()) {
          if (vFile.equals(file.getVirtualFile())) {
            removeBuildFile(file);
            break;
          }
        }
        for (Iterator<Map.Entry<VirtualFile,VirtualFile>> it = myAntFileToContextFileMap.entrySet().iterator(); it.hasNext();) {
          final Map.Entry<VirtualFile, VirtualFile> entry = it.next();
          if (vFile.equals(entry.getKey()) || vFile.equals(entry.getValue())) {
            it.remove();
          }
        }
      }
    }, project);
  }


  public Element getState() {
    try {
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

        final List<VirtualFile> files = new ArrayList<>(myAntFileToContextFileMap.keySet());
        // sort in order to minimize changes
        Collections.sort(files, (o1, o2) -> o1.getUrl().compareTo(o2.getUrl()));
        for (VirtualFile file : files) {
          final Element element = new Element(CONTEXT_MAPPING);
          final VirtualFile contextFile = myAntFileToContextFileMap.get(file);
          element.setAttribute(URL, file.getUrl());
          element.setAttribute(CONTEXT, contextFile.getUrl());
          state.addContent(element);
        }
      });
      return state;
    }
    catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  public void loadState(Element state) {
    myIsInitialized = Boolean.FALSE;
    try {
      myAntWorkspaceConfiguration.loadFromProjectSettings(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
      return;
    }

    List<Pair<Element, String>> files = new ArrayList<>();
    for (Iterator<Element> iterator = state.getChildren(BUILD_FILE).iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      iterator.remove();
      String url = element.getAttributeValue(URL);
      if (url != null) {
        files.add(Pair.create(element, url));
      }
    }

    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    // contexts
    myAntFileToContextFileMap.clear();
    for (Element element : state.getChildren(CONTEXT_MAPPING)) {
      String url = element.getAttributeValue(URL);
      String contextUrl = element.getAttributeValue(CONTEXT);
      VirtualFile file = vfManager.findFileByUrl(url);
      VirtualFile contextFile = vfManager.findFileByUrl(contextUrl);
      if (file != null && contextFile != null) {
        myAntFileToContextFileMap.put(file, contextFile);
      }
    }

    getProperties().readExternal(state);
    runWhenInitialized(() -> {
      String title = AntBundle.message("loading.ant.config.progress");
      queueLater(new Task.Backgroundable(getProject(), title, false) {
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
                  final VirtualFile vFile = buildFile.getVirtualFile();
                  final String buildFileUrl = vFile != null? vFile.getUrl() : null;

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
                    else if ("beforeRun".equals(eventId)) {
                      /*
                      for compatibility with previous format

                      <buildFile url="file://$PROJECT_DIR$/module/src/support-scripts.xml">
                        <executeOn event="beforeRun" target="prebuild-steps" runConfigurationType="Application" runConfigurationName="Main" />
                      </buildFile>
                      */
                      final String configType = e.getAttributeValue("runConfigurationType");
                      final String configName = e.getAttributeValue("runConfigurationName");
                      convertToBeforeRunTask(myProject, buildFileUrl, targetName, configType, configName);
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
                  myIsInitialized = Boolean.TRUE;
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

  private volatile Boolean myIsInitialized;
  private volatile Thread myInitThread;
  
  public boolean isInitialized() {
    final Boolean initialized = myIsInitialized;
    return initialized == null || initialized.booleanValue();
  }

  public AntBuildFile[] getBuildFiles() {
    //noinspection SuspiciousToArrayCall
    return myBuildFiles.toArray(new AntBuildFileBase[myBuildFiles.size()]);
  }

  @Override
  public List<AntBuildFileBase> getBuildFileList() {
    return myBuildFiles;
  }

  public AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    final AntBuildFile[] result = new AntBuildFile[]{null};
    final AntNoFileException[] ex = new AntNoFileException[]{null};
    final String title = AntBundle.message("register.ant.build.progress", file.getPresentableUrl());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), title, false) {
      @Nullable
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Ant", "Ant Task Finished", "");
      }

      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          incModificationCount();
          ApplicationManager.getApplication().runReadAction(() -> {
            try {
              result[0] = addBuildFileImpl(file);
              updateRegisteredActions();
            }
            catch (AntNoFileException e) {
              ex[0] = e;
            }
          });
          if (result[0] != null) {
            ApplicationManager.getApplication().invokeLater(() -> myEventDispatcher.getMulticaster().buildFileAdded(result[0]));
          }
        }
        finally {
          indicator.popState();
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return result[0];
  }

  public void removeBuildFile(@NotNull AntBuildFile file) {
    incModificationCount();
    removeBuildFileImpl(file);
    myBuildFiles.remove(((AntBuildFileBase)file));
    updateRegisteredActions();
  }

  public void addAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public boolean isFilterTargets() {
    return myAntWorkspaceConfiguration.FILTER_TARGETS;
  }

  public void setFilterTargets(final boolean value) {
    myAntWorkspaceConfiguration.FILTER_TARGETS = value;
  }

  public AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile) {
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
    if (events.size() == 0) {
      return AntBuildTargetBase.EMPTY_ARRAY;
    }
    final List<AntBuildTargetBase> targets = new ArrayList<>();
    for (ExecutionEvent event : events) {
      final MetaTarget target = (MetaTarget)getTargetForEvent(event);
      if (target != null && buildFile.equals(target.getBuildFile())) {
        targets.add(target);
      }
    }
    return targets.toArray(new AntBuildTargetBase[targets.size()]);
  }

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

  @Nullable
  public AntBuildTarget getTargetForEvent(final ExecutionEvent event) {
    final Pair<AntBuildFile, String> pair;
    synchronized (myEventToTargetMap) {
      pair = myEventToTargetMap.get(event);
    }
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
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
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

  public void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.put(event, Pair.create(buildFile, targetName));
    }
  }

  public void clearTargetForEvent(final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.remove(event);
    }
  }

  public void updateBuildFile(final AntBuildFile buildFile) {
    incModificationCount();
    myEventDispatcher.getMulticaster().buildFileChanged(buildFile);
    updateRegisteredActions();
  }

  public boolean isAutoScrollToSource() {
    return myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoScrollToSource(final boolean value) {
    myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE = value;
  }

  public AntInstallation getProjectDefaultAnt() {
    return DEFAULT_ANT.get(getProperties()).find(GlobalAntConfiguration.getInstance());
  }

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
      Collections.sort(events, EventElementComparator.INSTANCE);
      for (Element eventElement : events) {
        element.addContent(eventElement);
      }
    }
  }

  public AntBuildModelBase getModel(@NotNull AntBuildFile buildFile) {
    AntBuildModelBase model = myModelToBuildFileMap.get(buildFile);
    if (model == null) {
      model = createModel(buildFile);
      myModelToBuildFileMap.put(buildFile, model);
    }
    return model;
  }

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

  private AntBuildModelBase createModel(final AntBuildFile buildFile) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // otherwise commitAllDocuments() must have been called before the whole process was started
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    return new AntBuildModelImpl(buildFile);
  }

  private AntBuildFileBase addBuildFileImpl(final VirtualFile file) throws AntNoFileException {
    PsiFile xmlFile = myPsiManager.findFile(file);
    if (!(xmlFile instanceof XmlFile)) {
      throw new AntNoFileException("the file is not an xml file", file);
    }
    AntSupport.markFileAsAntFile(file, xmlFile.getProject(), true);
    if (!AntDomFileDescription.isAntFile(((XmlFile)xmlFile))) {
      throw new AntNoFileException("the file is not recognized as an ANT file", file);
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
    for (final AntBuildFile buildFile : myBuildFiles) {
      final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      String defaultTargetActionId = model.getDefaultTargetActionId();
      if (defaultTargetActionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, TargetAction.DEFAULT_TARGET_NAME, new String[]{TargetAction.DEFAULT_TARGET_NAME}, null);
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
      final Set<String> registeredIds = StringSetSpinAllocator.alloc();
      try {
        for (Pair<String, AnAction> pair : actionList) {
          if (!registeredIds.contains(pair.first)) {
            registeredIds.add(pair.first);
            actionManager.registerAction(pair.first, pair.second);
          }
        }
      }
      finally {
        StringSetSpinAllocator.dispose(registeredIds);
      }
    }
  }

  private static void collectTargetActions(final AntBuildTarget[] targets,
                                           final List<Pair<String, AnAction>> actionList,
                                           final AntBuildFile buildFile) {
    for (final AntBuildTarget target : targets) {
      final String actionId = ((AntBuildTargetBase)target).getActionId();
      if (actionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, target.getName(), new String[]{target.getName()}, target.getNotEmptyDescription());
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

  public boolean executeTargetBeforeCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteBeforeCompilationEvent.getInstance());
  }

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
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      try {
        final Project project = dataContext.getData(CommonDataKeys.PROJECT);
        if (project == null || project.isDisposed()) {
          targetDone.up();
        }
        else {
          target.run(dataContext, additionalProperties, new AntBuildListener() {
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

  private List<ExecutionEvent> getEventsByClass(Class eventClass) {
    final Thread initThread = myInitThread;
    if (initThread == null || initThread != Thread.currentThread()) {
      ensureInitialized();
    }
    final List<ExecutionEvent> list = new ArrayList<>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        if (eventClass.isInstance(event)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  private static void convertToBeforeRunTask(Project project, String buildFileUrl, String targetName, String configType, String configName) {
    if (buildFileUrl == null || targetName == null || configType == null) {
      return;
    }
    final RunManagerImpl runManager = (RunManagerImpl)RunManagerEx.getInstanceEx(project);
    final ConfigurationType type = runManager.getConfigurationType(configType);
    if (type == null) {
      return;
    }
    if (configName != null) {
      for (RunConfiguration configuration : runManager.getConfigurationsList(type)) {
        if (configName.equals(configuration.getName())) {
          final List<AntBeforeRunTask> tasks = runManager.getBeforeRunTasks(configuration, AntBeforeRunTaskProvider.ID);
          if (!tasks.isEmpty()) {
            AntBeforeRunTask task = tasks.get(0);//This is legacy code, we had only one task that time
            task.setEnabled(true);
            task.setTargetName(targetName);
            task.setAntFileUrl(buildFileUrl);
          }
        }
      }
    }
    else {
      for (ConfigurationFactory factory : type.getConfigurationFactories()) {
        final RunConfiguration template = runManager.getConfigurationTemplate(factory).getConfiguration();
        final List<AntBeforeRunTask> tasks = runManager.getBeforeRunTasks(template, AntBeforeRunTaskProvider.ID);
        if (!tasks.isEmpty()) {
          AntBeforeRunTask task = tasks.get(0);//This is legacy code, we had only one task that time
          task.setEnabled(true);
          task.setTargetName(targetName);
          task.setAntFileUrl(buildFileUrl);
        }
      }
    }
  }

  private static void queueLater(final Task task) {
    final Application app = ApplicationManager.getApplication();
    if (!app.isDispatchThread() || task.isHeadless()) {
      // for headless tasks we need to ensure async execution. 
      // Otherwise calls to AntConfiguration.getInstance() from the task will cause SOE
      app.invokeLater(task::queue, ModalityState.any());
    }
    else {
      task.queue();
    }
  }

  public void setContextFile(@NotNull XmlFile file, @Nullable XmlFile context) {
    if (context != null) {
      myAntFileToContextFileMap.put(file.getVirtualFile(), context.getVirtualFile());
    }
    else {
      myAntFileToContextFileMap.remove(file.getVirtualFile());
    }
  }

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

  @Nullable
  public XmlFile getEffectiveContextFile(final XmlFile file) {
    return new Object() {
      @Nullable XmlFile findContext(final XmlFile file, Set<PsiElement> processed) {
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
}
