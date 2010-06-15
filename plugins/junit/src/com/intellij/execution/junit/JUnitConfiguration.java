/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit2.configuration.JUnitConfigurable;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JUnitConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule>
  implements CommonJavaRunConfigurationParameters, RefactoringListenerProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.JUnitConfiguration");
  public static final String DEFAULT_PACKAGE_NAME = ExecutionBundle.message("default.package.presentable.name");

  @NonNls public static final String TEST_CLASS = "class";
  @NonNls public static final String TEST_PACKAGE = "package";
  @NonNls public static final String TEST_METHOD = "method";
  @NonNls private static final String PATTERN_EL_NAME = "pattern";
  @NonNls public static final String TEST_PATTERN = PATTERN_EL_NAME;

  @NonNls private static final String TEST_CLASS_ATT_NAME = "testClass";
  @NonNls private static final String ENABLED_ATT_NAME = "enabled";
  @NonNls private static final String PATTERNS_EL_NAME = "patterns";

  private final Data myData;
  // See #26522
  @NonNls public static final String JUNIT_START_CLASS = "com.intellij.rt.execution.junit.JUnitStarter";

  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;

  public JUnitConfiguration(final String name, final Project project, ConfigurationFactory configurationFactory) {
    this(name, project, new Data(), configurationFactory);
  }

  private JUnitConfiguration(final String name, final Project project, final Data data, ConfigurationFactory configurationFactory) {
    super(name, new JavaRunConfigurationModule(project, false), configurationFactory);
    myData = data;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return TestObject.fromString(myData.TEST_OBJECT, getProject(), this, env.getRunnerSettings(), env.getConfigurationSettings());
  }


  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<JUnitConfiguration> group = new SettingsEditorGroup<JUnitConfiguration>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new JUnitConfigurable(getProject()));
    RunConfigurationExtension.appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel());
    return group;
  }

  public Data getPersistentData() {
    return myData;
  }

  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    return myData.getTestObject(getProject(), this).getListener(element, this);
  }

  public String getGeneratedName() {
    return myData.getGeneratedName(getConfigurationModule());
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    myData.getTestObject(getProject(), this).checkConfiguration();
    RunConfigurationExtension.checkConfigurationIsValid(this);
  }

  public Collection<Module> getValidModules() {
    if (TEST_PACKAGE.equals(myData.TEST_OBJECT) || TEST_PATTERN.equals(myData.TEST_OBJECT)) {
      return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
    }
    try {
      myData.getTestObject(getProject(), this).checkConfiguration();
    }
    catch (RuntimeConfigurationError e) {
      return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
    }
    catch (RuntimeConfigurationException e) {
      //ignore
    }

    return JavaRunConfigurationModule.getModulesForClass(getProject(), myData.getMainClassName());
  }

  protected ModuleBasedConfiguration createInstance() {
    return new JUnitConfiguration(getName(), getProject(), myData.clone(), JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);// throw new RuntimeException("Should not call");
  }

  public boolean isGeneratedName() {
    final String name = getName();
    return myData.isGeneratedName(name, getConfigurationModule());
  }

  public String suggestedName() {
    return getTestObject().suggestActionName();
  }

  public void setVMParameters(String value) {
    myData.setVMParameters(value);
  }

  public String getVMParameters() {
    return myData.getVMParameters();
  }

  public void setProgramParameters(String value) {
    myData.setProgramParameters(value);
  }

  public String getProgramParameters() {
    return myData.getProgramParameters();
  }

  public void setWorkingDirectory(String value) {
    myData.setWorkingDirectory(value);
  }

  public String getWorkingDirectory() {
    return myData.getWorkingDirectory();
  }

  public void setEnvs(@NotNull Map<String, String> envs) {
    myData.setEnvs(envs);
  }

  @NotNull
  public Map<String, String> getEnvs() {
    return myData.getEnvs();
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    myData.PASS_PARENT_ENVS = passParentEnvs;
  }

  public boolean isPassParentEnvs() {
    return myData.PASS_PARENT_ENVS;
  }

  public boolean isAlternativeJrePathEnabled() {
    return ALTERNATIVE_JRE_PATH_ENABLED;
  }

  public void setAlternativeJrePathEnabled(boolean enabled) {
    this.ALTERNATIVE_JRE_PATH_ENABLED = enabled;
  }

  public String getAlternativeJrePath() {
    return ALTERNATIVE_JRE_PATH;
  }

  public void setAlternativeJrePath(String path) {
    this.ALTERNATIVE_JRE_PATH = path;
  }

  public String getRunClass() {
    final Data data = getPersistentData();
    return data.TEST_OBJECT != TEST_CLASS && data.TEST_OBJECT != TEST_METHOD ? null : data.getMainClassName();
  }

  public String getPackage() {
    final Data data = getPersistentData();
    return data.TEST_OBJECT != TEST_PACKAGE ? null : data.getPackageName();
  }


  public void beClassConfiguration(final PsiClass testClass) {
    setMainClass(testClass);
    myData.TEST_OBJECT = TEST_CLASS;
    setGeneratedName();
  }

  public void setMainClass(final PsiClass testClass) {
    final boolean shouldUpdateName = isGeneratedName();
    setModule(myData.setMainClass(testClass));
    if (shouldUpdateName) setGeneratedName();
  }

  public void setGeneratedName() {
    setName(getGeneratedName());
  }

  public void beMethodConfiguration(final Location<PsiMethod> methodLocation) {
    setModule(myData.setTestMethod(methodLocation));
    setGeneratedName();
  }

  @NotNull
  public Module[] getModules() {
    if ((TEST_PACKAGE.equals(myData.TEST_OBJECT) || TEST_PATTERN.equals(myData.TEST_OBJECT)) &&
        getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT) {
      return Module.EMPTY_ARRAY;
    }
    return super.getModules();
  }

  final RefactoringListeners.Accessor<PsiPackage> myPackage = new RefactoringListeners.Accessor<PsiPackage>() {
    public void setName(final String qualifiedName) {
      final boolean generatedName = isGeneratedName();
      myData.PACKAGE_NAME = qualifiedName;
      if (generatedName) setGeneratedName();
    }

    public PsiPackage getPsiElement() {
      final String qualifiedName = myData.getPackageName();
      return qualifiedName != null ? JavaPsiFacade.getInstance(getProject()).findPackage(qualifiedName)
             : null;
    }

    public void setPsiElement(final PsiPackage psiPackage) {
      setName(psiPackage.getQualifiedName());
    }
  };

  final RefactoringListeners.Accessor<PsiClass> myClass = new RefactoringListeners.Accessor<PsiClass>() {
    public void setName(@NotNull final String qualifiedName) {
      final boolean generatedName = isGeneratedName();
      myData.MAIN_CLASS_NAME = qualifiedName;
      if (generatedName) setGeneratedName();
    }

    public PsiClass getPsiElement() {
      return getConfigurationModule().findClass(myData.getMainClassName());
    }

    public void setPsiElement(final PsiClass psiClass) {
      final Module originalModule = getConfigurationModule().getModule();
      setMainClass(psiClass);
      restoreOriginalModule(originalModule);
    }
  };

  public TestObject getTestObject() {
    return myData.getTestObject(getProject(), this);
  }

  public void readExternal(final Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    RunConfigurationExtension.readSettings(this, element);
    readModule(element);
    DefaultJDOMExternalizer.readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(getPersistentData(), element);
    EnvironmentVariablesComponent.readExternal(element, getPersistentData().getEnvs());
    final Element patternsElement = element.getChild(PATTERNS_EL_NAME);
    if (patternsElement != null) {
      final LinkedHashMap<String, Boolean> tests = new LinkedHashMap<String, Boolean>();
      for (Object o : patternsElement.getChildren(PATTERN_EL_NAME)) {
        Element patternElement = (Element)o;
        tests.put(patternElement.getAttributeValue(TEST_CLASS_ATT_NAME), Boolean.valueOf(patternElement.getAttributeValue(ENABLED_ATT_NAME)));
      }
      myData.setPatterns(tests);
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    RunConfigurationExtension.writeSettings(this, element);
    writeModule(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    DefaultJDOMExternalizer.writeExternal(getPersistentData(), element);
    EnvironmentVariablesComponent.writeExternal(element, getPersistentData().getEnvs());
    final Element patternsElement = new Element(PATTERNS_EL_NAME);
    for (String o : getPersistentData().getPatterns().keySet()) {
      final Element patternElement = new Element(PATTERN_EL_NAME);
      patternElement.setAttribute(TEST_CLASS_ATT_NAME, o);
      patternElement.setAttribute(ENABLED_ATT_NAME, String.valueOf(getPersistentData().getPatterns().get(o)));
      patternsElement.addContent(patternElement);
    }
    element.addContent(patternsElement);
    PathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  public void configureClasspath(final JavaParameters javaParameters) throws CantRunException {
    RunConfigurationModule module = getConfigurationModule();
    final String jreHome = isAlternativeJrePathEnabled() ? getAlternativeJrePath() : null;
    final int pathType = JavaParameters.JDK_AND_CLASSES_AND_TESTS;
    if (myData.getScope() == TestSearchScope.WHOLE_PROJECT) {
      JavaParametersUtil.configureProject(module.getProject(), javaParameters, pathType, jreHome);
    }
    else {
      JavaParametersUtil.configureModule(module, javaParameters, pathType, jreHome);
    }
  }

  public static class Data implements Cloneable {
    public String PACKAGE_NAME;
    public String MAIN_CLASS_NAME;
    public String METHOD_NAME;
    public String TEST_OBJECT = TEST_CLASS;
    public String VM_PARAMETERS;
    public String PARAMETERS;
    public String WORKING_DIRECTORY;
    private Map<String, Boolean> myPattern = new LinkedHashMap<String, Boolean>();

    //iws/ipr compatibility
    public String ENV_VARIABLES;
    private Map<String, String> myEnvs = new LinkedHashMap<String, String>();
    public boolean PASS_PARENT_ENVS = true;

    public TestSearchScope.Wrapper TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();

    public boolean equals(final Object object) {
      if (!(object instanceof Data)) return false;
      final Data second = (Data)object;
      return Comparing.equal(TEST_OBJECT, second.TEST_OBJECT) &&
             Comparing.equal(getMainClassName(), second.getMainClassName()) &&
             Comparing.equal(getPackageName(), second.getPackageName()) &&
             Comparing.equal(getMethodName(), second.getMethodName()) &&
             Comparing.equal(getWorkingDirectory(), second.getWorkingDirectory()) &&
             Comparing.equal(VM_PARAMETERS, second.VM_PARAMETERS) &&
             Comparing.equal(PARAMETERS, second.PARAMETERS) &&
             Comparing.equal(myPattern, second.myPattern);
    }

    public int hashCode() {
      return Comparing.hashcode(TEST_OBJECT) ^
             Comparing.hashcode(getMainClassName()) ^
             Comparing.hashcode(getPackageName()) ^
             Comparing.hashcode(getMethodName()) ^
             Comparing.hashcode(getWorkingDirectory()) ^
             Comparing.hashcode(VM_PARAMETERS) ^
             Comparing.hashcode(PARAMETERS) ^
             Comparing.hashcode(myPattern);
    }

    public TestSearchScope getScope() {
      return TEST_SEARCH_SCOPE.getScope();
    }

    public Data clone() {
      try {
        Data data = (Data)super.clone();
        data.TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();
        data.setScope(getScope());
        return data;
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    public void setVMParameters(String value) {
      VM_PARAMETERS = value;
    }

    public String getVMParameters() {
      return VM_PARAMETERS;
    }

    public void setProgramParameters(String value) {
      PARAMETERS = value;
    }

    public String getProgramParameters() {
      return PARAMETERS;
    }

    public void setWorkingDirectory(String value) {
      WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
    }

    public String getWorkingDirectory() {
      return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
    }

    public Module setTestMethod(final Location<PsiMethod> methodLocation) {
      final PsiMethod method = methodLocation.getPsiElement();
      METHOD_NAME = method.getName();
      TEST_OBJECT = TEST_METHOD;
      return setMainClass(methodLocation instanceof MethodLocation ? ((MethodLocation)methodLocation).getContainingClass() : method.getContainingClass());
    }

    public boolean isGeneratedName(final String name, final JavaRunConfigurationModule configurationModule) {
      if (TEST_OBJECT == null) return true;
      if ((TEST_CLASS.equals(TEST_OBJECT) || TEST_METHOD.equals(TEST_OBJECT)) && getMainClassName().length() == 0) {
        return JavaExecutionUtil.isNewName(name);
      }
      if (TEST_METHOD.equals(TEST_OBJECT) && getMethodName().length() == 0) {
        return JavaExecutionUtil.isNewName(name);
      }
      return Comparing.equal(name, getGeneratedName(configurationModule));
    }

    public String getGeneratedName(final JavaRunConfigurationModule configurationModule) {
      if (TEST_PACKAGE.equals(TEST_OBJECT)) {
        final String moduleName = TEST_SEARCH_SCOPE.getScope() == TestSearchScope.WHOLE_PROJECT ? "" : configurationModule.getModuleName();
        final String packageName = getPackageName();
        if (packageName.length() == 0) {
          if (moduleName.length() > 0) {
            return ExecutionBundle.message("default.junit.config.name.all.in.module", moduleName);
          }
          return DEFAULT_PACKAGE_NAME;
        }
        if (moduleName.length() > 0) {
          return ExecutionBundle.message("default.junit.config.name.all.in.package.in.module", packageName, moduleName);
        }
        return packageName;
      }
      if (TEST_PATTERN.equals(TEST_OBJECT)) {
        return getPatternPresentation();
      }
      final String className = JavaExecutionUtil.getPresentableClassName(getMainClassName(), configurationModule);
      if (TEST_METHOD.equals(TEST_OBJECT)) {
        return className + '.' + getMethodName();
      }

      return className;
    }

    public String getMainClassName() {
      return MAIN_CLASS_NAME != null ? MAIN_CLASS_NAME : "";
    }

    public String getPackageName() {
      return PACKAGE_NAME != null ? PACKAGE_NAME : "";
    }

    public String getMethodName() {
      return METHOD_NAME != null ? METHOD_NAME : "";
    }

    public Map<String, Boolean> getPatterns() {
      return myPattern;
    }

    public String getPatternPresentation() {
      return getPatternPresentation(myPattern);
    }

    public static String getPatternPresentation(final Map<String, Boolean> patterns) {
      final List<String> enabledTests = new ArrayList<String>();
      for (String pattern : patterns.keySet()) {
        if (patterns.get(pattern)) {
          enabledTests.add(StringUtil.getShortName(pattern));
        }
      }
      return StringUtil.join(enabledTests, "||");
    }

    public void setPatterns(Map<String, Boolean> pattern) {
      myPattern = pattern;
    }

    public TestObject getTestObject(final Project project, final JUnitConfiguration configuration) {
      //TODO[dyoma]!
      return TestObject.fromString(TEST_OBJECT, project, configuration, null, null);
    }

    public Module setMainClass(final PsiClass testClass) {
      MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(testClass);
      PsiPackage containingPackage = JUnitUtil.getContainingPackage(testClass);
      PACKAGE_NAME = containingPackage != null ? containingPackage.getQualifiedName() : "";
      return JavaExecutionUtil.findModule(testClass);
    }

    public void setScope(final TestSearchScope scope) {
      TEST_SEARCH_SCOPE.setScope(scope);
    }

    public Map<String, String> getEnvs() {
      return myEnvs;
    }

    public void setEnvs(final Map<String, String> envs) {
      myEnvs = envs;
    }
  }

}
