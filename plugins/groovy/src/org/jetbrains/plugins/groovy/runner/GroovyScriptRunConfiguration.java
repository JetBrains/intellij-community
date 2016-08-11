/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.execution.util.ScriptFileUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyRunnerPsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * @author peter
 */
public class GroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, RefactoringListenerProvider {

  private static final Logger LOG = Logger.getInstance(GroovyScriptRunConfiguration.class);
  private String vmParams;
  private String workDir;
  private boolean isDebugEnabled;
  @Nullable private String scriptParams;
  @Nullable private String scriptPath;
  private final Map<String, String> envs = new LinkedHashMap<>();
  public boolean passParentEnv = true;

  public GroovyScriptRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new RunConfigurationModule(project), factory);
    workDir = PathUtil.getLocalPath(project.getBaseDir());
  }

  public void setWorkDir(String dir) {
    workDir = dir;
  }

  public String getWorkDir() {
    return workDir;
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  @Override
  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final GroovyScriptRunner scriptRunner = findConfiguration();
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
  private GroovyScriptRunner findConfiguration() {
    final VirtualFile scriptFile = getScriptFile();
    if (scriptFile == null) {
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(scriptFile);
    if (!(psiFile instanceof GroovyFile)) {
      return null;
    }
    if (!((GroovyFile)psiFile).isScript()) {
      return new DefaultGroovyScriptRunner();
    }

    return GroovyScriptUtil.getScriptType((GroovyFile)psiFile).getRunner();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    readModule(element);
    scriptPath = ExternalizablePath.localPathValue(JDOMExternalizer.readString(element, "path"));
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    final String wrk = JDOMExternalizer.readString(element, "workDir");
    if (!".".equals(wrk)) {
      workDir = ExternalizablePath.localPathValue(wrk);
    }
    isDebugEnabled = Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug"));
    envs.clear();
    JDOMExternalizer.readMap(element, envs, null, "env");
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "path", ExternalizablePath.urlValue(scriptPath));
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", ExternalizablePath.urlValue(workDir));
    JDOMExternalizer.write(element, "debug", isDebugEnabled);
    JDOMExternalizer.writeMap(element, envs, null, "env");
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    final VirtualFile script = getScriptFile();
    if (script == null) {
      throw new CantRunException("Cannot find script " + scriptPath);
    }

    final GroovyScriptRunner scriptRunner = findConfiguration();
    if (scriptRunner == null) {
      throw new CantRunException("Unknown script type " + scriptPath);
    }

    final Module module = ObjectUtils.chooseNotNull(getModule(), ContainerUtil.getFirstItem(getValidModules()));
    if (!scriptRunner.ensureRunnerConfigured(module, this, executor, getProject())) {
      return null;
    }

    final boolean tests = ProjectRootManager.getInstance(getProject()).getFileIndex().isInTestSourceContent(script);

    return new JavaCommandLineState(environment) {
      @NotNull
      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        handler.setShouldDestroyProcessRecursively(true);
        if (scriptRunner.shouldRefreshAfterFinish()) {
          handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(ProcessEvent event) {
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
        JavaParameters params = createJavaParametersWithSdk(module);
        ProgramParametersUtil.configureConfiguration(params, GroovyScriptRunConfiguration.this);
        scriptRunner.configureCommandLine(params, module, tests, script, GroovyScriptRunConfiguration.this);

        return params;
      }
    };
  }

  public void setScriptParameters(String scriptParameters) {
    scriptParams = scriptParameters;
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

  @Nullable
  private VirtualFile getScriptFile() {
    return ScriptFileUtil.findScriptFileByPath(scriptPath);
  }

  @Nullable
  private PsiClass getScriptClass() {
    final VirtualFile scriptFile = getScriptFile();
    if (scriptFile == null) return null;
    final PsiFile file = PsiManager.getInstance(getProject()).findFile(scriptFile);
    return GroovyRunnerPsiUtil.getRunningClass(file);
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final PsiClass toRun = getScriptClass();
    if (toRun == null) {
      throw new RuntimeConfigurationWarning(GroovyBundle.message("class.does.not.exist"));
    }
    if (toRun instanceof GrTypeDefinition) {
      if (!GroovyRunnerPsiUtil.canBeRunByGroovy(toRun)) {
        throw new RuntimeConfigurationWarning(GroovyBundle.message("class.can't be executed"));
      }
    }
    else if (!(toRun instanceof GroovyScriptClass)) {
      throw new RuntimeConfigurationWarning(GroovyBundle.message("script.file.is.not.groovy.file"));
    }
  }

  @Override
  public void setVMParameters(String value) {
    vmParams = value;
  }

  @Override
  public String getVMParameters() {
    return vmParams;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getAlternativeJrePath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAlternativeJrePath(String path) {
    throw new UnsupportedOperationException();
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
    LOG.error("Don't add program parameters to Groovy script run configuration. Use Script parameters instead");
  }

  @Override
  public String getProgramParameters() {
    return null;
  }

  @Nullable
  public String getScriptParameters() {
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

  @Nullable
  public String getScriptPath() {
    return scriptPath;
  }

  public void setScriptPath(@Nullable String scriptPath) {
    this.scriptPath = scriptPath;
  }
}
