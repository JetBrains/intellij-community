/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.ExternalizablePath;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ScriptFileUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyRunnerPsiUtil;
import org.jetbrains.plugins.groovy.runner.util.CommonProgramRunConfigurationParametersDelegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static com.intellij.execution.util.ProgramParametersUtil.configureConfiguration;

/**
 * @author peter
 */
public class GroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, RefactoringListenerProvider {

  private String vmParams;
  private String workDir;
  private boolean isDebugEnabled;
  private boolean isAddClasspathToTheRunner;
  @Nullable private String scriptParams;
  @Nullable private String scriptPath;
  private final Map<String, String> envs = new LinkedHashMap<>();
  public boolean passParentEnv = true;

  private boolean myAlternativeJrePathEnabled;
  private @Nullable String myAlternativeJrePath;

  public GroovyScriptRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new RunConfigurationModule(project), factory);
    workDir = PathUtil.getLocalPath(project.getBaseDir());
  }

  @Nullable
  public Module getModule() {
    return ObjectUtils.chooseNotNull(getConfigurationModule().getModule(), ContainerUtil.getFirstItem(getValidModules()));
  }

  @Override
  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final GroovyScriptRunner scriptRunner = getScriptRunner();
    if (scriptRunner == null) {
      return Arrays.asList(modules);
    }


    ArrayList<Module> res = new ArrayList<>();
    for (Module module : modules) {
      if (scriptRunner.isValidModule(module)) {
        res.add(module);
      }
    }
    return res;
  }

  @Nullable
  private GroovyScriptRunner getScriptRunner() {
    final VirtualFile scriptFile = ScriptFileUtil.findScriptFileByPath(getScriptPath());
    if (scriptFile == null) return null;

    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(scriptFile);
    if (!(psiFile instanceof GroovyFile)) return null;

    final GroovyFile groovyFile = (GroovyFile)psiFile;
    if (groovyFile.isScript()) {
      return GroovyScriptUtil.getScriptType(groovyFile).getRunner();
    }
    else {
      return new DefaultGroovyScriptRunner();
    }
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    scriptPath = ExternalizablePath.localPathValue(JDOMExternalizer.readString(element, "path"));
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    final String wrk = JDOMExternalizer.readString(element, "workDir");
    if (!".".equals(wrk)) {
      workDir = ExternalizablePath.localPathValue(wrk);
    }
    isDebugEnabled = Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug"));
    isAddClasspathToTheRunner = Boolean.parseBoolean(JDOMExternalizer.readString(element, "addClasspath"));
    envs.clear();
    JDOMExternalizer.readMap(element, envs, null, "env");

    myAlternativeJrePathEnabled = JDOMExternalizer.readBoolean(element, "alternativeJrePathEnabled");
    myAlternativeJrePath = JDOMExternalizer.readString(element, "alternativeJrePath");
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizer.write(element, "path", ExternalizablePath.urlValue(scriptPath));
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", ExternalizablePath.urlValue(workDir));
    JDOMExternalizer.write(element, "debug", isDebugEnabled);
    if (isAddClasspathToTheRunner) JDOMExternalizer.write(element, "addClasspath", true);
    JDOMExternalizer.writeMap(element, envs, null, "env");

    if (myAlternativeJrePathEnabled) {
      JDOMExternalizer.write(element, "alternativeJrePathEnabled", true);
      if (StringUtil.isNotEmpty(myAlternativeJrePath)) JDOMExternalizer.write(element, "alternativeJrePath", myAlternativeJrePath);
    }
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
    final VirtualFile scriptFile = ScriptFileUtil.findScriptFileByPath(getScriptPath());
    if (scriptFile == null) return null;

    final GroovyScriptRunner scriptRunner = getScriptRunner();
    if (scriptRunner == null) return null;

    return new JavaCommandLineState(environment) {
      @NotNull
      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        handler.setShouldDestroyProcessRecursively(true);
        if (scriptRunner.shouldRefreshAfterFinish()) {
          handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              if (!ApplicationManager.getApplication().isDisposed()) {
                VirtualFileManager.getInstance().asyncRefresh(null);
              }
            }
          });
        }

        return handler;
      }

      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        final Module module = getModule();
        final boolean tests = ProjectRootManager.getInstance(getProject()).getFileIndex().isInTestSourceContent(scriptFile);
        String jrePath = isAlternativeJrePathEnabled() ? getAlternativeJrePath() : null;
        JavaParameters params = new JavaParameters();
        params.setUseClasspathJar(true);
        params.setDefaultCharset(getProject());
        params.setJdk(
          module == null ? JavaParametersUtil.createProjectJdk(getProject(), jrePath)
                         : JavaParametersUtil.createModuleJdk(module, !tests, jrePath)
        );
        configureConfiguration(params, new CommonProgramRunConfigurationParametersDelegate(GroovyScriptRunConfiguration.this) {
          @Nullable
          @Override
          public String getProgramParameters() {
            return null;
          }
        });
        scriptRunner.configureCommandLine(params, module, tests, scriptFile, GroovyScriptRunConfiguration.this);

        return params;
      }
    };
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (scriptPath == null || !scriptPath.equals(getPathByElement(element))) {
      return null;
    }

    final PsiClass classToRun = GroovyRunnerPsiUtil.getRunningClass(element);

    if (element instanceof GroovyFile) {
      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof GroovyFile) {
            GroovyFile file = (GroovyFile)newElement;
            setScriptPath(ScriptFileUtil.getScriptFilePath(file.getVirtualFile()));
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          elementRenamedOrMoved(newElement);
        }
      };
    }
    else if (element instanceof PsiClass && element.getManager().areElementsEquivalent(element, classToRun)) {
      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          setName(((PsiClass)newElement).getName());
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          elementRenamedOrMoved(newElement);
        }
      };
    }
    return null;
  }

  @SuppressWarnings("Duplicates")
  @Nullable
  private static String getPathByElement(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) return null;
    return vfile.getPath();
  }

  public static JavaParameters createJavaParametersWithSdk(@Nullable Module module) {
    JavaParameters params = new JavaParameters();
    params.setCharset(null);

    if (module != null) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
        params.setJdk(sdk);
      }
    }
    if (params.getJdk() == null) {
      params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));
    }
    return params;
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor(getProject());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    final String scriptPath = getScriptPath();

    final VirtualFile script = ScriptFileUtil.findScriptFileByPath(scriptPath);
    if (script == null) throw new RuntimeConfigurationException("Cannot find script " + scriptPath);

    final GroovyScriptRunner scriptRunner = getScriptRunner();
    if (scriptRunner == null) throw new RuntimeConfigurationException("Unknown script type " + scriptPath);

    scriptRunner.ensureRunnerConfigured(this);

    final PsiFile file = PsiManager.getInstance(getProject()).findFile(script);
    final PsiClass toRun = GroovyRunnerPsiUtil.getRunningClass(file);
    if (toRun == null) {
      throw new RuntimeConfigurationWarning(GroovyBundle.message("class.does.not.exist"));
    }
    if (toRun instanceof GrTypeDefinition) {
      if (!GroovyRunnerPsiUtil.canBeRunByGroovy(toRun)) {
        throw new RuntimeConfigurationWarning(GroovyBundle.message("class.cannot.be.executed"));
      }
    }
    else {
      throw new RuntimeConfigurationWarning(GroovyBundle.message("script.file.is.not.groovy.file"));
    }
    JavaParametersUtil.checkAlternativeJRE(this);
  }

  @Override
  public void setVMParameters(@Nullable String value) {
    vmParams = value;
  }

  @Override
  public String getVMParameters() {
    return vmParams;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    return myAlternativeJrePathEnabled;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean alternativeJrePathEnabled) {
    myAlternativeJrePathEnabled = alternativeJrePathEnabled;
  }

  @Nullable
  @Override
  public String getAlternativeJrePath() {
    return myAlternativeJrePath;
  }

  @Override
  public void setAlternativeJrePath(@Nullable String alternativeJrePath) {
    myAlternativeJrePath = alternativeJrePath;
  }

  @Override
  public String getRunClass() {
    return null;
  }

  @Override
  public String getPackage() {
    return null;
  }

  @Override
  public void setProgramParameters(@Nullable String value) {
    scriptParams = value;
  }

  @Override
  public String getProgramParameters() {
    return scriptParams;
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    workDir = value;
  }

  @Override
  public String getWorkingDirectory() {
    return workDir;
  }

  @Override
  public void setEnvs(@NotNull Map<String, String> envs) {
    this.envs.clear();
    this.envs.putAll(envs);
  }

  @NotNull
  @Override
  public Map<String, String> getEnvs() {
    return envs;
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    this.passParentEnv = passParentEnvs;
  }

  @Override
  public boolean isPassParentEnvs() {
    return passParentEnv;
  }

  public boolean isDebugEnabled() {
    return isDebugEnabled;
  }

  public void setDebugEnabled(boolean debugEnabled) {
    isDebugEnabled = debugEnabled;
  }

  public boolean isAddClasspathToTheRunner() {
    return isAddClasspathToTheRunner;
  }

  public void setAddClasspathToTheRunner(boolean addClasspathToTheRunner) {
    isAddClasspathToTheRunner = addClasspathToTheRunner;
  }

  @Nullable
  public String getScriptPath() {
    return scriptPath;
  }

  public void setScriptPath(@Nullable String scriptPath) {
    this.scriptPath = scriptPath;
  }

  @Override
  public GlobalSearchScope getSearchScope() {
    GlobalSearchScope superScope = super.getSearchScope();

    String path = getScriptPath();
    if (path == null) return superScope;

    VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (scriptFile == null) return superScope;

    GlobalSearchScope fileScope = GlobalSearchScope.fileScope(getProject(), scriptFile);
    if (superScope == null) return fileScope;

    return new DelegatingGlobalSearchScope(fileScope.union(superScope)) {
      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        if (file1.equals(scriptFile)) return 1;
        if (file2.equals(scriptFile)) return -1;
        return super.compare(file1, file2);
      }
    };
  }
}
