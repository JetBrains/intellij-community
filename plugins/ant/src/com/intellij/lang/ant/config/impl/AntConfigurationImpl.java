package com.intellij.lang.ant.config.impl;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.DataAccessor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ActionRunner;
import com.intellij.util.EventDispatcher;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ValueProperty;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(
  name = "AntConfiguration",
  storages = {@Storage(id = "default", file = "$PROJECT_FILE$"), @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/ant.xml",
                                                                          scheme = StorageScheme.DIRECTORY_BASED)})
public class AntConfigurationImpl extends AntConfigurationBase implements PersistentStateComponent<Element>, ModificationTracker {

  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<AntReference>("defaultAnt", AntReference.BUNDLED_ANT);
  public static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<AntConfiguration>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<String>() {
    public String getName() {
      return "$defaultJDKName";
    }

    @Nullable
    public String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Nullable
    public String get(final AbstractPropertyContainer container) {
      if (!container.hasProperty(this)) return null;
      AntConfiguration antConfiguration = AntConfigurationImpl.INSTANCE.get(container);
      return ProjectRootManager.getInstance(antConfiguration.getProject()).getProjectJdkName();
    }

    public String copy(final String jdkName) {
      return jdkName;
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntConfigurationImpl");
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String URL = "url";
  @NonNls private static final String EXECUTE_ON_ELEMENT = "executeOn";
  @NonNls private static final String EVENT_ELEMENT = "event";
  @NonNls private static final String TARGET_ELEMENT = "target";

  private final PsiManager myPsiManager;
  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap =
    new HashMap<ExecutionEvent, Pair<AntBuildFile, String>>();
  private final List<AntBuildFile> myBuildFiles = new ArrayList<AntBuildFile>();
  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = new HashMap<AntBuildFile, AntBuildModelBase>();
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);
  private final AntWorkspaceConfiguration myAntWorkspaceConfiguration;
  private final StartupManager myStartupManager;
  private volatile long myModificationCount = 0;

  public AntConfigurationImpl(final Project project, final AntWorkspaceConfiguration antWorkspaceConfiguration) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myAntWorkspaceConfiguration = antWorkspaceConfiguration;
    myPsiManager = PsiManager.getInstance(project);
    myStartupManager = StartupManager.getInstance(project);
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private volatile boolean myIsInitialized = false;
  
  public boolean isInitialized() {
    return myIsInitialized;
  }

  public AntBuildFile[] getBuildFiles() {
    return myBuildFiles.toArray(new AntBuildFile[myBuildFiles.size()]);
  }

  public AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    final AntBuildFile[] result = new AntBuildFile[]{null};
    final AntNoFileException[] ex = new AntNoFileException[]{null};
    final String title = AntBundle.message("register.ant.build.progress", file.getPresentableUrl());
    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), title, false) {
      public void run(final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          myModificationCount++;
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                result[0] = addBuildFileImpl(file);
                updateRegisteredActions();
              }
              catch (AntNoFileException e) {
                ex[0] = e;
              }
            }
          });
          if (result[0] != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                myEventDispatcher.getMulticaster().buildFileAdded(result[0]);
              }
            });
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

  public void removeBuildFile(final AntBuildFile file) {
    myModificationCount++;
    removeBuildFileImpl(file);
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
    final List<AntBuildTargetBase> targets = new ArrayList<AntBuildTargetBase>();
    for (ExecutionEvent event : events) {
      final MetaTarget target = (MetaTarget)getTargetForEvent(event);
      if (target != null && buildFile.equals(target.getBuildFile())) {
        targets.add(target);
      }
    }
    return targets.toArray(new AntBuildTargetBase[targets.size()]);
  }

  public List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target) {
    final List<ExecutionEvent> list = new ArrayList<ExecutionEvent>();
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
      return null; // file was removed
    }
    final String targetName = (String)pair.second;

    final AntBuildTarget antBuildTarget = buildFile.getModel().findTarget(targetName);
    if (antBuildTarget != null) {
      return antBuildTarget;
    }
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
    if (events.size() == 0) {
      return null;
    }
    for (ExecutionEvent ev : events) {
      final String presentableName = ev.getPresentableName();
      if (Comparing.strEqual(targetName, presentableName)) {
        return new MetaTarget(buildFile, presentableName, ((ExecuteCompositeTargetEvent)ev).getTargetNames());
      }
    }
    return null;
  }

  public void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.put(event, new Pair<AntBuildFile, String>(buildFile, targetName));
    }
  }

  public void clearTargetForEvent(final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.remove(event);
    }
  }

  public void updateBuildFile(final AntBuildFile buildFile) {
    myModificationCount++;
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
  public AntBuildModel getModelIfRegistered(final AntBuildFile buildFile) {
    if (!myBuildFiles.contains(buildFile)) return null;
    return getModel(buildFile);
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  private void readExternal(final Element parentNode) throws InvalidDataException {
    myAntWorkspaceConfiguration.loadFromProjectSettings(parentNode);
    getProperties().readExternal(parentNode);
    runWhenInitialized(new Runnable() {
      public void run() {
        loadBuildFileProjectProperties(parentNode);
      }
    });
  }

  private void runWhenInitialized(final Runnable runnable) {
    if (getProject().isInitialized()) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
    else {
      myStartupManager.registerPostStartupActivity(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
  }

  private void writeExternal(final Element parentNode) throws WriteExternalException {
    getProperties().writeExternal(parentNode);
    final ActionRunner.InterruptibleRunnable action = new ActionRunner.InterruptibleRunnable() {
      public void run() throws WriteExternalException {
        for (final AntBuildFile buildFile : myBuildFiles) {
          Element element = new Element(BUILD_FILE);
          element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
          ((AntBuildFileBase)buildFile).writeProperties(element);
          saveEvents(element, buildFile);
          parentNode.addContent(element);
        }
      }
    };
    try {
      ActionRunner.runInsideReadAction(action);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
      throw e;
    }
    catch (RuntimeException e) {
      LOG.error(e);
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void saveEvents(final Element element, final AntBuildFile buildFile) {
    List<Element> events = null;
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final Pair<AntBuildFile, String> pair = myEventToTargetMap.get(event);
        if (!buildFile.equals(pair.first)) {
          continue;
        }
        Element eventElement = new Element(EXECUTE_ON_ELEMENT);
        eventElement.setAttribute(EVENT_ELEMENT, event.getTypeId());
        eventElement.setAttribute(TARGET_ELEMENT, pair.second);
        event.writeExternal(eventElement);
        if (events == null) {
          events = new ArrayList<Element>();
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

  public AntBuildModel getModel(final AntBuildFile buildFile) {
    AntBuildModelBase model = myModelToBuildFileMap.get(buildFile);
    if (model == null) {
      model = createModel(buildFile);
      myModelToBuildFileMap.put(buildFile, model);
    }
    return model;
  }

  @Nullable
  public AntBuildFile findBuildFileByActionId(final String id) {
    for (AntBuildFile buildFile : getBuildFiles()) {
      AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      if (id.equals(model.getDefaultTargetActionId())) {
        return buildFile;
      }
      if (model.hasTargetWithActionId(id)) return buildFile;
    }
    return null;
  }

  public boolean hasTasksToExecuteBeforeRun(final RunConfiguration configuration) {
    return findExecuteBeforeRunEvent(configuration) != null;
  }

  public boolean executeTaskBeforeRun(final DataContext context, final RunConfiguration configuration) {
    final ExecuteBeforeRunEvent foundEvent = findExecuteBeforeRunEvent(configuration);
    return runTargetSynchronously(context, foundEvent);
  }

  public AntBuildTarget getTargetForBeforeRunEvent(ConfigurationType type, String runConfigurationName) {
    ExecutionEvent event = new ExecuteBeforeRunEvent(type, runConfigurationName);
    return getTargetForEvent(event);
  }

  public void setTargetForBeforeRunEvent(final AntBuildFile buildFile,
                                         final String targetName,
                                         ConfigurationType type,
                                         String runConfigurationName) {
    setTargetForEvent(buildFile, targetName, new ExecuteBeforeRunEvent(type, runConfigurationName));
  }

  private AntBuildModelBase createModel(final AntBuildFile buildFile) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // otherwise commitAllDocuments() must have been called before the whole process was started
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    return new AntBuildModelImpl(buildFile);
  }

  private AntBuildFileBase addBuildFileImpl(final VirtualFile file) throws AntNoFileException {
    PsiFile psiFile = myPsiManager.findFile(file);
    if (psiFile == null) {
      throw new AntNoFileException(AntBundle.message("cant.add.file.error.message"), file);
    }
    AntSupport.markFileAsAntFile(file, psiFile.getViewProvider(), true);
    psiFile = AntSupport.getAntFile(psiFile);
    if (psiFile == null) {
      throw new AntNoFileException(AntBundle.message("cant.add.file.error.message"), file);
    }
    final AntFile antFile = (AntFile)psiFile;
    final AntBuildFileImpl buildFile = new AntBuildFileImpl(antFile, this);
    antFile.getSourceElement().putCopyableUserData(XmlFile.ANT_BUILD_FILE, buildFile);
    myBuildFiles.add(buildFile);
    return buildFile;
  }

  private void updateRegisteredActions() {

    final List<Pair<String, AnAction>> actionList = new ArrayList<Pair<String, AnAction>>();
    for (final AntBuildFile buildFile : getBuildFiles()) {
      final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      String defaultTargetActionId = model.getDefaultTargetActionId();
      if (defaultTargetActionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, TargetAction.DEFAULT_TARGET_NAME, new String[]{TargetAction.DEFAULT_TARGET_NAME}, null);
        actionList.add(new Pair<String, AnAction>(defaultTargetActionId, action));
      }

      collectTargetActions(model.getFilteredTargets(), actionList, buildFile);
      collectTargetActions(getMetaTargets(buildFile), actionList, buildFile);
    }

    synchronized (this) {
      // unregister Ant actions
      ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(getProject()));
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
        actionList.add(new Pair<String, AnAction>(actionId, action));
      }
    }
  }

  private void removeBuildFileImpl(AntBuildFile buildFile) {
    final XmlFile xmlFile = ((AntFile)buildFile.getAntFile()).getSourceElement();
    xmlFile.putCopyableUserData(XmlFile.ANT_BUILD_FILE, null);
    AntSupport.markFileAsAntFile(xmlFile.getVirtualFile(), xmlFile.getViewProvider(), false);
    myBuildFiles.remove(buildFile);
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
    final Semaphore targetDone = new Semaphore();
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {

        public void run() {
          Project project = DataAccessor.PROJECT.from(dataContext);
          if (project == null || project.isDisposed()) {
            result[0] = false;
            return;
          }
          targetDone.down();
          target.run(dataContext, new AntBuildListener() {
            public void buildFinished(int state, int errorCount) {
              result[0] = (state == AntBuildListener.FINISHED_SUCCESSFULLY) && (errorCount == 0);
              targetDone.up();
            }
          });
        }
      }, ModalityState.NON_MODAL);
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    targetDone.waitFor();
    return result[0];
  }

  private List<ExecutionEvent> getEventsByClass(Class eventClass) {
    final List<ExecutionEvent> list = new ArrayList<ExecutionEvent>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        if (eventClass.isInstance(event)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  private void loadBuildFileProjectProperties(final Element parentNode) {
    final List<Pair<Element, VirtualFile>> files = new ArrayList<Pair<Element, VirtualFile>>();
    for (final Object o : parentNode.getChildren(BUILD_FILE)) {
      final Element element = (Element)o;
      final String url = element.getAttributeValue(URL);
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null) {
        files.add(new Pair<Element, VirtualFile>(element, file));
      }
    }
    final String title = AntBundle.message("loading.ant.config.progress");
    new Task.Backgroundable(getProject(), title, false) {
      public void run(final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                for (Pair<Element, VirtualFile> pair : files) {
                  final Element element = pair.getFirst();
                  final VirtualFile file = pair.getSecond();
                  try {
                    final AntBuildFileBase buildFile = addBuildFileImpl(file);
                    buildFile.readProperties(element);

                    for (final Object o1 : element.getChildren(EXECUTE_ON_ELEMENT)) {
                      Element e = (Element)o1;
                      String eventId = e.getAttributeValue(EVENT_ELEMENT);
                      ExecutionEvent event = null;
                      String targetName = e.getAttributeValue(TARGET_ELEMENT);
                      if (ExecuteBeforeCompilationEvent.TYPE_ID.equals(eventId)) {
                        event = ExecuteBeforeCompilationEvent.getInstance();
                      }
                      else if (ExecuteAfterCompilationEvent.TYPE_ID.equals(eventId)) {
                        event = ExecuteAfterCompilationEvent.getInstance();
                      }
                      else if (ExecuteBeforeRunEvent.TYPE_ID.equals(eventId)) {
                        event = new ExecuteBeforeRunEvent();
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
                        event.readExternal(e);
                        setTargetForEvent(buildFile, targetName, event);
                      }
                    }

                  }
                  catch (AntNoFileException ignored) {
                  }
                  catch (InvalidDataException e) {
                    LOG.error(e);
                  }
                }
                AntWorkspaceConfiguration.getInstance(getProject()).loadFileProperties();
              }
              catch (InvalidDataException e) {
                LOG.error(e);
              }
              finally {
                updateRegisteredActions();
                myIsInitialized = true;
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    myEventDispatcher.getMulticaster().configurationLoaded();
                  }
                });
              }
            }
          });
        }
        finally {
          indicator.popState();
        }
      }
    }.queueLater(null);
  }

  @Nullable
  ExecuteBeforeRunEvent findExecuteBeforeRunEvent(RunConfiguration configuration) {
    final ConfigurationType type = configuration.getType();
    for (final ExecutionEvent e : getEventsByClass(ExecuteBeforeRunEvent.class)) {
      final ExecuteBeforeRunEvent event = (ExecuteBeforeRunEvent)e;
      if (type.equals(event.getConfigurationType())) {
        if (event.getRunConfigurationName() == null) {
          // run for any configuration of this type
          return event;
        }
        if (event.getRunConfigurationName().equals(configuration.getName())) {
          return event;
        }
      }
    }
    return null;
  }

  private static class EventElementComparator implements Comparator<Element> {
    static final Comparator<? super Element> INSTANCE = new EventElementComparator();

    public int compare(final Element o1, final Element o2) {
      final int typesEqual = o1.getAttributeValue(EVENT_ELEMENT).compareTo(o2.getAttributeValue(EVENT_ELEMENT));
      return typesEqual == 0 ? o1.getAttributeValue(TARGET_ELEMENT).compareTo(o2.getAttributeValue(TARGET_ELEMENT)) : typesEqual;
    }
  }
}
