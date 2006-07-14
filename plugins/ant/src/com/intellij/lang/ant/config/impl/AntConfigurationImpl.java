package com.intellij.lang.ant.config.impl;

import com.intellij.ant.AntBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.DataAccessor;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ActionRunner;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AntConfigurationImpl extends AntConfigurationBase implements JDOMExternalizable, ModificationTracker {

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
  private final static Object myLock = new Object();

  private final PsiManager myPsiManager;
  private final ToolWindowManager myToolWindowManager;
  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap =
    new HashMap<ExecutionEvent, Pair<AntBuildFile, String>>();
  private final List<AntBuildFile> myBuildFiles = new ArrayList<AntBuildFile>();
  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = new HashMap<AntBuildFile, AntBuildModelBase>();
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);
  private final AntWorkspaceConfiguration myAntWorkspaceConfiguration;
  private final StartupManager myStartupManager;
  private long myModificationCount = 0;
  private AntExplorer myAntExplorer;

  public AntConfigurationImpl(final Project project, final AntWorkspaceConfiguration antWorkspaceConfiguration) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myAntWorkspaceConfiguration = antWorkspaceConfiguration;
    myPsiManager = PsiManager.getInstance(project);
    myToolWindowManager = ToolWindowManager.getInstance(project);
    myStartupManager = StartupManager.getInstance(project);
  }

  public void projectOpened() {
    final CompilerManager compilerManager = CompilerManager.getInstance(getProject());
    final DataContext dataContext = MapDataContext.singleData(DataConstants.PROJECT, getProject());
    compilerManager.addBeforeTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return executeTargetBeforeCompile(dataContext);
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return executeTargetAfterCompile(dataContext);
      }
    });

    StartupManager.getInstance(getProject()).registerPostStartupActivity(new Runnable() {
      public void run() {
        myAntExplorer = new AntExplorer(getProject());
        ToolWindow toolWindow = myToolWindowManager.registerToolWindow(ToolWindowId.ANT_BUILD, myAntExplorer, ToolWindowAnchor.RIGHT);
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowAnt.png"));
      }
    });
  }

  public void projectClosed() {
    updateRegisteredActions();
    if (myAntExplorer != null) {
      myToolWindowManager.unregisterToolWindow(ToolWindowId.ANT_BUILD);
      myAntExplorer.dispose();
      myAntExplorer = null;
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "AntConfiguration";
  }

  public AntBuildFile[] getBuildFiles() {
    return myBuildFiles.toArray(new AntBuildFile[myBuildFiles.size()]);
  }

  public AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    myModificationCount++;
    final AntBuildFile buildFile = addBuildFileImpl(file);
    updateRegisteredActions();
    myPsiManager.getCachedValuesManager().releaseOutdatedValues();
    return buildFile;
  }

  public void removeBuildFile(final AntBuildFile file) {
    myModificationCount++;
    removeBuildFileImpl(file);
    updateRegisteredActions();
    myPsiManager.getCachedValuesManager().releaseOutdatedValues();
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
    for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
      final AntBuildTarget targetForEvent = getTargetForEvent(event);
      if (target.equals(targetForEvent)) {
        list.add(event);
      }
    }
    return list;
  }

  @Nullable
  public AntBuildTarget getTargetForEvent(final ExecutionEvent event) {
    final Pair pair = myEventToTargetMap.get(event);
    if (pair == null) {
      return null;
    }
    final AntBuildFileBase buildFile = (AntBuildFileBase)pair.first;
    if (!myBuildFiles.contains(buildFile)) {
      return null; // file was removed
    }
    final String targetName = (String)pair.second;
    if (ExecuteCompositeTargetEvent.TYPE_ID.equals(event.getTypeId())) {
      final ExecuteCompositeTargetEvent _event = (ExecuteCompositeTargetEvent)event;
      return new MetaTarget(buildFile, _event.getPresentableName(), _event.getTargetNames());
    }
    return buildFile.getModel().findTarget(targetName);
  }

  public void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event) {
    myEventToTargetMap.put(event, new Pair<AntBuildFile, String>(buildFile, targetName));
  }

  public void clearTargetForEvent(final ExecutionEvent event) {
    myEventToTargetMap.remove(event);
  }

  public void updateBuildFile(final AntBuildFile buildFile) {
    myModificationCount++;
    myPsiManager.getCachedValuesManager().releaseOutdatedValues();
    myEventDispatcher.getMulticaster().buildFileChanged(buildFile);
    updateRegisteredActions();
  }

  public boolean isAutoScrollToSource() {
    return myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoScrollToSource(final boolean value) {
    myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE = value;
  }

  @Nullable
  public AntBuildModel getModelIfRegistered(final AntBuildFile buildFile) {
    if (!myBuildFiles.contains(buildFile)) return null;
    return getModel(buildFile);
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    myAntWorkspaceConfiguration.loadFromProjectSettings(parentNode);
    getProperties().readExternal(parentNode);
    final Runnable action = new Runnable() {
      public void run() {
        try {
          loadBuildFileProjectProperties(parentNode);
          loadBuildFileWorkspaceProperties();
        }
        catch (InvalidDataException e) {
          LOG.error(e);
        }
        updateRegisteredActions();
      }
    };
    myStartupManager.registerStartupActivity(new Runnable() {
      public void run() {
        LOG.info("Start up");
        ApplicationManager.getApplication().runReadAction(action);
      }
    });
  }

  public void writeExternal(final Element parentNode) throws WriteExternalException {
    getProperties().writeExternal(parentNode);
    final ActionRunner.InterruptibleRunnable action = new ActionRunner.InterruptibleRunnable() {
      public void run() throws WriteExternalException {
        for (final AntBuildFile buildFile : myBuildFiles) {
          Element element = new Element(BUILD_FILE);
          element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
          ((AntBuildFileBase)buildFile).writeProperties(element);
          for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
            Pair pair = myEventToTargetMap.get(event);
            if (buildFile.equals(pair.first)) {
              Element eventElement = new Element(EXECUTE_ON_ELEMENT);
              eventElement.setAttribute(EVENT_ELEMENT, event.getTypeId());
              eventElement.setAttribute(TARGET_ELEMENT, (String)pair.second);
              event.writeExternal(eventElement);
              element.addContent(eventElement);
            }
          }

          parentNode.addContent(element);
        }
      }
    };
    try {
      ActionRunner.runInsideReadAction(action);
    }
    catch (Exception e) {
      if (e instanceof WriteExternalException) throw(WriteExternalException)e;
      if (e instanceof RuntimeException) throw(RuntimeException)e;
      LOG.error(e);
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

  private AntBuildModelBase createModel(final AntBuildFile buildFile) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    });
    return new AntBuildModelImpl(buildFile);
  }

  private AntBuildFile addBuildFileImpl(final VirtualFile file) throws AntNoFileException {
    PsiFile psiFile = myPsiManager.findFile(file);
    if (psiFile != null) {
      psiFile = psiFile.getViewProvider().getPsi(AntSupport.getLanguage());
    }
    if (psiFile == null) throw new AntNoFileException(AntBundle.message("cant.add.file.error.message"), file);
    AntBuildFileImpl buildFile = new AntBuildFileImpl((AntFile)psiFile, this);
    myBuildFiles.add(buildFile);

    myEventDispatcher.getMulticaster().buildFileAdded(buildFile);
    return buildFile;
  }

  private static void updateRegisteredActions() {
    synchronized (myLock) {
      // unregister Ant actions
      ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      final String[] oldIds = actionManager.getActionIds(TargetAction.ACTION_ID_PREFIX);
      for (String oldId : oldIds) {
        actionManager.unregisterAction(oldId);
      }

      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      Set<String> registeredIds = StringSetSpinAllocator.alloc();
      try {
        for (Project project : openProjects) {
          final AntConfiguration antConfiguration = AntConfiguration.getInstance(project);
          for (final AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
            final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
            String defaultTargetActionId = model.getDefaultTargetActionId();
            if (defaultTargetActionId != null && !registeredIds.contains(defaultTargetActionId)) {
              registeredIds.add(defaultTargetActionId);
              actionManager.registerAction(defaultTargetActionId, new TargetAction(buildFile, TargetAction.DEFAULT_TARGET_NAME,
                                                                                   new String[]{TargetAction.DEFAULT_TARGET_NAME}, null));
            }

            registerTargetActions(model.getFilteredTargets(), registeredIds, actionManager, buildFile);
            registerTargetActions(antConfiguration.getMetaTargets(buildFile), registeredIds, actionManager,
                                  buildFile);
          }
        }
      }
      finally {
        StringSetSpinAllocator.dispose(registeredIds);
      }
    }

  }

  private static void registerTargetActions(final AntBuildTarget[] targets,
                                            final Set<String> registeredIds,
                                            final ActionManagerEx actionManager,
                                            final AntBuildFile buildFile) {
    for (final AntBuildTarget target : targets) {
      final String actionId = ((AntBuildTargetBase)target).getActionId();
      if (actionId == null) {
        continue;
      }
      if (registeredIds.contains(actionId)) {
        continue;
      }
      registeredIds.add(actionId);
      actionManager.registerAction(actionId, new TargetAction(buildFile, target.getName(), new String[]{target.getName()},
                                                              target.getNotEmptyDescription()));
    }
  }

  private void removeBuildFileImpl(AntBuildFile buildFile) {
    myBuildFiles.remove(buildFile);
    myModelToBuildFileMap.remove(buildFile);
    myEventDispatcher.getMulticaster().buildFileRemoved(buildFile);
  }

  private boolean executeTargetBeforeCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteBeforeCompilationEvent.getInstance());
  }

  private boolean executeTargetAfterCompile(final DataContext context) {
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
      }, ModalityState.NON_MMODAL);
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
    for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
      if (eventClass.isInstance(event)) {
        list.add(event);
      }
    }
    return list;
  }

  private void loadBuildFileProjectProperties(final Element parentNode) throws InvalidDataException {
    for (final Object o : parentNode.getChildren(BUILD_FILE)) {
      final Element element = (Element)o;
      final String url = element.getAttributeValue(URL);
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) continue;
      PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile != null) {
        psiFile = psiFile.getViewProvider().getPsi(AntSupport.getLanguage());
      }
      if (!(psiFile instanceof AntFile)) continue;
      AntBuildFileBase buildFile = new AntBuildFileImpl((AntFile)psiFile, this);
      buildFile.readProperties(element);
      myBuildFiles.add(buildFile);
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
  }

  private void loadBuildFileWorkspaceProperties() throws InvalidDataException {
    AntWorkspaceConfiguration.getInstance(getProject()).loadFileProperties();
  }

  @Nullable
  private ExecuteBeforeRunEvent findExecuteBeforeRunEvent(RunConfiguration configuration) {
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
}
