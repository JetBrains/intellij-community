package org.jetbrains.plugins.gradle.remote;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiBundle;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManager;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManagerImpl;
import org.jetbrains.plugins.gradle.remote.impl.GradleApiFacadeImpl;
import org.jetbrains.plugins.gradle.remote.wrapper.GradleApiFacadeWrapper;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point to work with remote {@link GradleApiFacade}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 1:08 PM
 */
public class GradleApiFacadeManager {

  private static final Pair<GradleApiFacade, RemoteGradleProcessSettings> NULL_VALUE = Pair.empty();

  private static final String REMOTE_PROCESS_TTL_IN_MS_KEY = "gradle.remote.process.ttl.ms";

  private static final String MAIN_CLASS_NAME                      = GradleApiFacadeImpl.class.getName();
  private static final int    REMOTE_FAIL_RECOVERY_ATTEMPTS_NUMBER = 3;

  private final ConcurrentMap<String /*project name*/, GradleApiFacade>                                    myFacadeWrappers
    = new ConcurrentWeakHashMap<String, GradleApiFacade>();
  private final ConcurrentMap<String /*project name*/, Pair<GradleApiFacade, RemoteGradleProcessSettings>> myRemoteFacades
    = new ConcurrentWeakHashMap<String, Pair<GradleApiFacade, RemoteGradleProcessSettings>>();

  @NotNull private final GradleInstallationManager myGradleInstallationManager;

  private final AtomicReference<RemoteGradleProgressNotificationManager> myExportedProgressManager
    = new AtomicReference<RemoteGradleProgressNotificationManager>();
  @NotNull private final GradleProgressNotificationManagerImpl myProgressManager;

  // Please note that we don't use RemoteGradleProcessSettings as the 'Configuration' type parameter here because we need
  // to apply the settings to the newly created process. I.e. every time new process is created we need to call
  // 'GradleApiFacade.applySettings()'. So, we need to hold reference to the last returned 'GradleApiFacade' stub anyway.
  private final RemoteProcessSupport<Object, GradleApiFacade, String> mySupport;

  public GradleApiFacadeManager(@NotNull GradleInstallationManager gradleInstallationManager,
                                @NotNull GradleProgressNotificationManager manager)
  {
    myGradleInstallationManager = gradleInstallationManager;
    myProgressManager = (GradleProgressNotificationManagerImpl)manager;
    mySupport = new RemoteProcessSupport<Object, GradleApiFacade, String>(GradleApiFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(Object o) {
        return GradleApiFacade.class.getName();
      }

      @Override
      protected RunProfileState getRunProfileState(Object o, String configuration, Executor executor) throws ExecutionException {
        return createRunProfileState(findProjectByName(configuration));
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        shutdown(false);
      }
    });
  }

  @NotNull
  private static Project findProjectByName(@NotNull String name) {
    final ProjectManager projectManager = ProjectManager.getInstance();
    for (Project project : projectManager.getOpenProjects()) {
      if (name.equals(project.getName())) {
        return project;
      }
    }
    return projectManager.getDefaultProject();
  }

  private RunProfileState createRunProfileState(@Nullable final Project project) {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {
        Collection<File> gradleLibraries = myGradleInstallationManager.getAllLibraries(project);
        GradleLog.LOG.assertTrue(gradleLibraries != null, GradleBundle.message("gradle.generic.text.error.sdk.undefined"));
        if (gradleLibraries == null) {
          throw new ExecutionException("Can't find gradle libraries");
        } 

        final SimpleJavaParameters params = new SimpleJavaParameters();
        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));

        params.setWorkingDirectory(PathManager.getBinPath());
        final List<String> classPath = new ArrayList<String>();
        classPath.addAll(PathManager.getUtilClassPath());
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(LanguageLevel.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(PsiBundle.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Alarm.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(DependencyScope.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(JavaSdkVersion.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(ExtensionPointName.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(OpenProjectFileChooserDescriptor.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(getClass()), classPath);
        for (File library : gradleLibraries) {
          classPath.add(library.getAbsolutePath());
        }
        params.getClassPath().addAll(classPath);
        addBundle(params.getClassPath(), "messages.CommonBundle");
        addBundle(params.getClassPath(), GradleBundle.PATH_TO_BUNDLE);

        params.setMainClass(MAIN_CLASS_NAME);
        
        params.getVMParametersList().addParametersString("-Djava.awt.headless=true -Xmx512m");
        
        // It may take a while for gradle api to resolve external dependencies. Default RMI timeout
        // is 15 seconds (http://download.oracle.com/javase/6/docs/technotes/guides/rmi/sunrmiproperties.html#connectionTimeout),
        // we don't want to get EOFException because of that.
        params.getVMParametersList().addParametersString(
          "-Dsun.rmi.transport.connectionTimeout=" + String.valueOf(TimeUnit.HOURS.toMillis(1))
        );
        //params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009");
        return params;
      }

      private void addBundle(@NotNull PathsList classPath, @NotNull String bundlePath) {
        String pathToUse = bundlePath.replace('.', '/');
        if (!pathToUse.endsWith(".properties")) {
          pathToUse += ".properties";
        }
        if (!pathToUse.startsWith("/")) {
          pathToUse = '/' + pathToUse;
        }
        classPath.add(PathManager.getResourceRoot(getClass(), pathToUse));
      }

      @NotNull
      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      @NotNull
      protected OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        Sdk sdk = params.getJdk();
        if (sdk == null) {
          throw new ExecutionException("No sdk is defined. Params: " + params);
        } 

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(
          ((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk),
          params,
          false
        );
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
          @Override
          public Charset getCharset() {
            return commandLine.getCharset();
          }
        };
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }
    };
  }

  public synchronized void shutdown(boolean wait) {
    mySupport.stopAll(wait);
  }
  
  /**
   * @return              gradle api facade to use
   * @throws Exception    in case of inability to return the facade
   */
  @NotNull
  public GradleApiFacade getFacade(@Nullable Project project) throws Exception {
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    final GradleApiFacade facade = myFacadeWrappers.get(project.getName());
    if (facade == null) {
      final GradleApiFacade newFacade = (GradleApiFacade)Proxy.newProxyInstance(
        GradleApiFacadeManager.class.getClassLoader(), new Class[]{GradleApiFacade.class}, new MyHandler(project)
      );
      myFacadeWrappers.putIfAbsent(project.getName(), newFacade);
    }
    return myFacadeWrappers.get(project.getName());
  }

  public Object doInvoke(@NotNull Project project, Method method, Object[] args, int invocationNumber) throws Throwable {
    GradleApiFacade facade = doGetFacade(project);
    try {
      return method.invoke(facade, args);
    }
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof RemoteException && invocationNumber > 0) {
        Thread.sleep(1000);
        return doInvoke(project, method, args, invocationNumber - 1);
      }
      else {
        throw e;
      }
    }
  }

  @SuppressWarnings("ConstantConditions")
  @NotNull
  private GradleApiFacade doGetFacade(@NotNull Project project) throws Exception {
    if (project.isDisposed() || !GradleUtil.isGradleAvailable(project)) {
      return GradleApiFacade.NULL_OBJECT;
    }
    Pair<GradleApiFacade, RemoteGradleProcessSettings> pair = myRemoteFacades.get(project.getName());
    if (pair != null) {
      if (isValid(pair, project)) {
        return pair.first;
      }
      mySupport.stopAll(true);
      myFacadeWrappers.clear();
      myRemoteFacades.clear();
      final Pair<GradleApiFacade, RemoteGradleProcessSettings> p = myRemoteFacades.putIfAbsent(project.getName(), NULL_VALUE);
      if (p != null && p != NULL_VALUE) {
        return p.first;
      }
    }

    final GradleApiFacade facade = mySupport.acquire(this, project.getName());
    if (facade == null) {
      throw new IllegalStateException("Can't obtain facade to working with gradle api at the remote process. Project: " + project);
    }
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        mySupport.stopAll(true);
        myFacadeWrappers.clear();
        myRemoteFacades.clear();
      }
    });
    final GradleApiFacade result = new GradleApiFacadeWrapper(facade, myProgressManager);
    Pair<GradleApiFacade, RemoteGradleProcessSettings> newPair
      = new Pair<GradleApiFacade, RemoteGradleProcessSettings>(result, getRemoteSettings(project));
    if (myRemoteFacades.putIfAbsent(project.getName(), newPair) != null && !myRemoteFacades.replace(project.getName(), NULL_VALUE, newPair)) {
      GradleLog.LOG.warn("Detected unexpected duplicate tooling api facade instance creation. Project: " + project);
      return myRemoteFacades.get(project.getName()).first;
    }
    if (!StringUtil.isEmpty(newPair.second.getJavaHome())) {
      GradleLog.LOG.info("Instructing gradle to use java from " + newPair.second.getJavaHome());
    }
    result.applySettings(newPair.second);
    RemoteGradleProgressNotificationManager exported = myExportedProgressManager.get();
    if (exported == null) {
      try {
        exported = (RemoteGradleProgressNotificationManager)UnicastRemoteObject.exportObject(myProgressManager, 0);
        myExportedProgressManager.set(exported);
      }
      catch (RemoteException e) {
        exported = myExportedProgressManager.get();
      }
    }
    if (exported == null) {
      GradleLog.LOG.warn("Can't export progress manager");
    }
    else {
      result.applyProgressManager(exported);
    }
    return result;
  }

  private boolean isValid(@NotNull Pair<GradleApiFacade, RemoteGradleProcessSettings> pair, @Nullable Project project) {
    if (pair == NULL_VALUE) {
      return false;
    }
    
    // Check remote process is alive.
    try {
      pair.first.getResolver();
    }
    catch (RemoteException e) {
      return false;
    }

    // Check that significant settings are not changed
    RemoteGradleProcessSettings oldSettings = pair.second;
    RemoteGradleProcessSettings currentSettings = getRemoteSettings(project);

    // We restart the slave process because there is a possible case that it was started with the incorrect classpath.
    // For example, it could be started with gradle milestone-3 and that means that its classpath doesn't contain BasicIdeaProject.class.
    // So, even if the user defines gradle milestone-7 to use, the slave process still is unable to operate because its classpath
    // is still not changed.
    //
    // Please note that that should be changed when we support gradle wrapper. I.e. minimum set of gradle binaries will be bundled
    // to the gradle plugin and they will contain all necessary binaries all the time.
    return StringUtil.equals(oldSettings.getGradleHome(), currentSettings.getGradleHome());
  }

  @NotNull
  private RemoteGradleProcessSettings getRemoteSettings(@Nullable Project project) {
    File gradleHome = myGradleInstallationManager.getGradleHome(project);
    assert gradleHome != null;
    RemoteGradleProcessSettings result = new RemoteGradleProcessSettings(gradleHome.getAbsolutePath());
    String ttlAsString = System.getProperty(REMOTE_PROCESS_TTL_IN_MS_KEY);
    if (ttlAsString != null) {
      try {
        long ttl = Long.parseLong(ttlAsString.trim());
        result.setTtlInMs(ttl);
      }
      catch (NumberFormatException e) {
        GradleLog.LOG.warn("Incorrect remote process ttl value detected. Expected to find number, found '" + ttlAsString + "'");
      }
    }
    GradleJavaHelper javaHelper = new GradleJavaHelper();
    result.setJavaHome(javaHelper.getJdkHome());
    return result;
  }
  
  private class MyHandler implements InvocationHandler {
    
    @NotNull private final String myProjectName;

    MyHandler(@NotNull Project project) {
      myProjectName = project.getName();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return doInvoke(findProjectByName(myProjectName), method, args, REMOTE_FAIL_RECOVERY_ATTEMPTS_NUMBER);
    }
  }
}
