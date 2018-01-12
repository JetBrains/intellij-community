/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.execution.junit;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit2.configuration.JUnitConfigurable;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JUnitConfiguration extends JavaTestConfigurationBase {
  public static final String DEFAULT_PACKAGE_NAME = ExecutionBundle.message("default.package.presentable.name");

  @NonNls public static final String TEST_CLASS = "class";
  @NonNls public static final String TEST_PACKAGE = "package";
  @NonNls public static final String TEST_DIRECTORY = "directory";
  @NonNls public static final String TEST_CATEGORY = "category";
  @NonNls public static final String TEST_METHOD = "method";
  @NonNls public static final String TEST_UNIQUE_ID = "uniqueId";
  @NonNls public static final String TEST_TAGS = "tags";
  @NonNls public static final String BY_SOURCE_POSITION = "source location";
  @NonNls public static final String BY_SOURCE_CHANGES = "changes";

  //fork modes
  @NonNls public static final String FORK_NONE = "none";
  @NonNls public static final String FORK_METHOD = "method";
  @NonNls public static final String FORK_KLASS = "class";
  @NonNls public static final String FORK_REPEAT = "repeat";
  // See #26522
  @NonNls public static final String JUNIT_START_CLASS = "com.intellij.rt.execution.junit.JUnitStarter";
  @NonNls private static final String PATTERN_EL_NAME = "pattern";
  @NonNls public static final String TEST_PATTERN = PATTERN_EL_NAME;
  @NonNls private static final String TEST_CLASS_ATT_NAME = "testClass";
  @NonNls private static final String PATTERNS_EL_NAME = "patterns";
  private final Data myData;
  final RefactoringListeners.Accessor<PsiPackage> myPackage = new RefactoringListeners.Accessor<PsiPackage>() {
    @Override
    public void setName(final String qualifiedName) {
      final boolean generatedName = isGeneratedName();
      myData.PACKAGE_NAME = qualifiedName;
      if (generatedName) setGeneratedName();
    }

    @Override
    public PsiPackage getPsiElement() {
      final String qualifiedName = myData.getPackageName();
      return qualifiedName != null ? JavaPsiFacade.getInstance(getProject()).findPackage(qualifiedName)
                                   : null;
    }

    @Override
    public void setPsiElement(final PsiPackage psiPackage) {
      setName(psiPackage.getQualifiedName());
    }
  };
  final RefactoringListeners.Accessor<PsiClass> myClass = new RefactoringListeners.Accessor<PsiClass>() {
    @Override
    public void setName(@NotNull final String qualifiedName) {
      final boolean generatedName = isGeneratedName();
      myData.MAIN_CLASS_NAME = qualifiedName;
      if (generatedName) setGeneratedName();
    }

    @Override
    public PsiClass getPsiElement() {
      return getConfigurationModule().findClass(myData.getMainClassName());
    }

    @Override
    public void setPsiElement(final PsiClass psiClass) {
      final Module originalModule = getConfigurationModule().getModule();
      setMainClass(psiClass);
      restoreOriginalModule(originalModule);
    }
  };
  
  final RefactoringListeners.Accessor<PsiClass> myCategory = new RefactoringListeners.Accessor<PsiClass>() {
    @Override
    public void setName(@NotNull final String qualifiedName) {
      setCategory(qualifiedName);
    }

    @Override
    public PsiClass getPsiElement() {
      return getConfigurationModule().findClass(myData.getCategory());
    }

    @Override
    public void setPsiElement(final PsiClass psiClass) {
      setCategory(JavaExecutionUtil.getRuntimeQualifiedName(psiClass));
    }
  };
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;

  public JUnitConfiguration(final String name, final Project project, ConfigurationFactory configurationFactory) {
    this(name, project, new Data(), configurationFactory);
  }

  protected JUnitConfiguration(final String name, final Project project, final Data data, ConfigurationFactory configurationFactory) {
    super(name, new JavaRunConfigurationModule(project, false), configurationFactory);
    myData = data;
  }

  @Override
  public TestObject getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return TestObject.fromString(myData.TEST_OBJECT, this, env);
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<JUnitConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new JUnitConfigurable(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  public Data getPersistentData() {
    return myData;
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    final RefactoringElementListener listener = getTestObject().getListener(element, this);
    return RunConfigurationExtension.wrapRefactoringElementListener(element, this, listener);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    getTestObject().checkConfiguration();
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public Collection<Module> getValidModules() {
    if (TEST_PACKAGE.equals(myData.TEST_OBJECT) || TEST_PATTERN.equals(myData.TEST_OBJECT)) {
      return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
    }
    try {
      getTestObject().checkConfiguration();
    }
    catch (RuntimeConfigurationError e) {
      return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
    }
    catch (RuntimeConfigurationException e) {
      //ignore
    }

    return JavaRunConfigurationModule.getModulesForClass(getProject(), myData.getMainClassName());
  }

  @Override
  public String suggestedName() {
    String repeat;
    switch (getRepeatMode()) {
      case RepeatCount.UNLIMITED :
      case RepeatCount.UNTIL_FAILURE :
        repeat = " [*]";
        break;
      case RepeatCount.N:
        repeat = " [" + getRepeatCount() + "]";
        break;
      default:
        repeat = "";
    }
    return myData.getGeneratedName(getConfigurationModule()) + repeat;
  }

  @Override
  public String getActionName() {
    return getTestObject().suggestActionName();
  }

  @Override
  public String getVMParameters() {
    return myData.getVMParameters();
  }

  @Override
  public void setVMParameters(@Nullable String value) {
    myData.setVMParameters(value);
  }

  @Override
  public String getProgramParameters() {
    return myData.getProgramParameters();
  }

  @Override
  public void setProgramParameters(String value) {
    myData.setProgramParameters(value);
  }

  @Override
  public String getWorkingDirectory() {
    return myData.getWorkingDirectory();
  }

  @Override
  public void setWorkingDirectory(String value) {
    myData.setWorkingDirectory(value);
  }

  @Override
  @NotNull
  public Map<String, String> getEnvs() {
    return myData.getEnvs();
  }

  @Override
  public void setEnvs(@NotNull Map<String, String> envs) {
    myData.setEnvs(envs);
  }

  @Override
  public boolean isPassParentEnvs() {
    return myData.PASS_PARENT_ENVS;
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    myData.PASS_PARENT_ENVS = passParentEnvs;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    return ALTERNATIVE_JRE_PATH_ENABLED;
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    ALTERNATIVE_JRE_PATH_ENABLED = enabled;
  }

  @Override
  public String getAlternativeJrePath() {
    return ALTERNATIVE_JRE_PATH;
  }

  @Override
  public void setAlternativeJrePath(String path) {
    ALTERNATIVE_JRE_PATH = path;
  }

  @Override
  public String getRunClass() {
    final Data data = getPersistentData();
    return !Comparing.strEqual(data.TEST_OBJECT, TEST_CLASS) &&
           !Comparing.strEqual(data.TEST_OBJECT, TEST_METHOD) ? null : data.getMainClassName();
  }

  @Override
  public String getPackage() {
    final Data data = getPersistentData();
    return !Comparing.strEqual(data.TEST_OBJECT, TEST_PACKAGE) ? null : data.getPackageName();
  }

  public void beClassConfiguration(final PsiClass testClass) {
    if (FORK_KLASS.equals(getForkMode())) {
      setForkMode(FORK_NONE);
    }
    setMainClass(testClass);
    myData.TEST_OBJECT = TEST_CLASS;
    setGeneratedName();
  }

  @Override
  public boolean isConfiguredByElement(PsiElement element) {
    final PsiClass testClass = JUnitUtil.getTestClass(element);
    final PsiMethod testMethod = JUnitUtil.getTestMethod(element, false);
    final PsiPackage testPackage;
    if (element instanceof PsiPackage) {
      testPackage = (PsiPackage)element;
    } else if (element instanceof PsiDirectory){
      testPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
    } else {
      testPackage = null;
    }
    PsiDirectory testDir = element instanceof PsiDirectory ? (PsiDirectory)element : null;

    return getTestObject().isConfiguredByElement(this, testClass, testMethod, testPackage, testDir);
  }

  @Override
  public TestSearchScope getTestSearchScope() {
    return getPersistentData().getScope();
  }

  public void beFromSourcePosition(PsiLocation<PsiMethod> sourceLocation) {
    myData.setTestMethod(sourceLocation);
    myData.TEST_OBJECT = BY_SOURCE_POSITION;
  }

  public void setMainClass(final PsiClass testClass) {
    final boolean shouldUpdateName = isGeneratedName();
    setModule(myData.setMainClass(testClass));
    if (shouldUpdateName) setGeneratedName();
  }

  public void setCategory(String categoryName) {
    final boolean shouldUpdateName = isGeneratedName();
    myData.setCategoryName(categoryName);
    if (shouldUpdateName) setGeneratedName();
  }

  public void beMethodConfiguration(final Location<PsiMethod> methodLocation) {
    setForkMode(FORK_NONE);
    setModule(myData.setTestMethod(methodLocation));
    setGeneratedName();
  }

  @Override
  @NotNull
  public Module[] getModules() {
    if (TEST_PACKAGE.equals(myData.TEST_OBJECT) &&
        getPersistentData().getScope() == TestSearchScope.WHOLE_PROJECT) {
      return Module.EMPTY_ARRAY;
    }
    return super.getModules();
  }

  public TestObject getTestObject() {
    return myData.getTestObject(this);
  }

  @Override
  public void readExternal(@NotNull final Element element) throws InvalidDataException {
    super.readExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(this, element);
    DefaultJDOMExternalizer.readExternal(getPersistentData(), element);
    EnvironmentVariablesComponent.readExternal(element, getPersistentData().getEnvs());
    final Element patternsElement = element.getChild(PATTERNS_EL_NAME);
    if (patternsElement != null) {
      final LinkedHashSet<String> tests = new LinkedHashSet<>();
      for (Element patternElement : patternsElement.getChildren(PATTERN_EL_NAME)) {
        tests.add(patternElement.getAttributeValue(TEST_CLASS_ATT_NAME));
      }
      myData.setPatterns(tests);
    }
    final Element forkModeElement = element.getChild("fork_mode");
    if (forkModeElement != null) {
      final String mode = forkModeElement.getAttributeValue("value");
      if (mode != null) {
        setForkMode(mode);
      }
    }
    final String count = element.getAttributeValue("repeat_count");
    if (count != null) {
      try {
        setRepeatCount(Integer.parseInt(count));
      }
      catch (NumberFormatException e) {
        setRepeatCount(1);
      }
    }
    final String repeatMode = element.getAttributeValue("repeat_mode");
    if (repeatMode != null) {
      setRepeatMode(repeatMode);
    }
    final Element dirNameElement = element.getChild("dir");
    if (dirNameElement != null) {
      final String dirName = dirNameElement.getAttributeValue("value");
      getPersistentData().setDirName(FileUtil.toSystemDependentName(dirName));
    }

    final Element categoryNameElement = element.getChild("category");
    if (categoryNameElement != null) {
      final String categoryName = categoryNameElement.getAttributeValue("value");
      getPersistentData().setCategoryName(categoryName);
    }

    Element idsElement = element.getChild("uniqueIds");
    if (idsElement != null) {
      List<String> ids = new ArrayList<>();
      idsElement.getChildren("uniqueId").forEach(uniqueIdElement -> ids.add(uniqueIdElement.getAttributeValue("value")));
      getPersistentData().setUniqueIds(ArrayUtil.toStringArray(ids));
    }
  }

  @Override
  public void writeExternal(@NotNull final Element element) throws WriteExternalException {
    super.writeExternal(element);
    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    final Data persistentData = getPersistentData();
    DefaultJDOMExternalizer.writeExternal(persistentData, element);

    if (!persistentData.getEnvs().isEmpty()) {
      EnvironmentVariablesComponent.writeExternal(element, persistentData.getEnvs());
    }

    final String dirName = persistentData.getDirName();
    if (!dirName.isEmpty()) {
      final Element dirNameElement = new Element("dir");
      dirNameElement.setAttribute("value", FileUtil.toSystemIndependentName(dirName));
      element.addContent(dirNameElement);
    }

    final String categoryName = persistentData.getCategory();
    if (!categoryName.isEmpty()) {
      final Element categoryNameElement = new Element("category");
      categoryNameElement.setAttribute("value", categoryName);
      element.addContent(categoryNameElement);
    }

    final Element patternsElement = new Element(PATTERNS_EL_NAME);
    for (String o : persistentData.getPatterns()) {
      final Element patternElement = new Element(PATTERN_EL_NAME);
      patternElement.setAttribute(TEST_CLASS_ATT_NAME, o);
      patternsElement.addContent(patternElement);
    }
    element.addContent(patternsElement);
    final String forkMode = getForkMode();
    if (!forkMode.equals("none")) {
      final Element forkModeElement = new Element("fork_mode");
      forkModeElement.setAttribute("value", forkMode);
      element.addContent(forkModeElement);
    }
    if (getRepeatCount() != 1) {
      element.setAttribute("repeat_count", String.valueOf(getRepeatCount()));
    }
    final String repeatMode = getRepeatMode();
    if (!RepeatCount.ONCE.equals(repeatMode)) {
      element.setAttribute("repeat_mode", repeatMode);
    }
    String[] ids = persistentData.getUniqueIds();
    if (ids != null) {
      Element uniqueIds = new Element("uniqueIds");
      Arrays.stream(ids).forEach(id -> uniqueIds.addContent(new Element("uniqueId").setAttribute("value", id)));
      element.addContent(uniqueIds);
    }
  }

  public String getForkMode() {
    return myData.FORK_MODE;
  }

  public void setForkMode(@NotNull String forkMode) {
    myData.FORK_MODE = forkMode;
  }

  @Override
  public boolean collectOutputFromProcessHandler() {
    return false;
  }

  public void bePatternConfiguration(List<PsiClass> classes, PsiMethod method) {
    myData.TEST_OBJECT = TEST_PATTERN;
    final LinkedHashSet<String> patterns = new LinkedHashSet<>();
    final String methodSufiix;
    if (method != null) {
      myData.METHOD_NAME = Data.getMethodPresentation(method);
      methodSufiix = "," + myData.METHOD_NAME;
    } else {
      methodSufiix = "";
    }
    for (PsiClass pattern : classes) {
      patterns.add(JavaExecutionUtil.getRuntimeQualifiedName(pattern) + methodSufiix);
    }
    myData.setPatterns(patterns);
    final Module module = RunConfigurationProducer.getInstance(PatternConfigurationProducer.class).findModule(this, getConfigurationModule()
      .getModule(), patterns);
    if (module == null) {
      myData.setScope(TestSearchScope.WHOLE_PROJECT);
      setModule(null);
    } else {
      setModule(module);
    }
    setGeneratedName();
  }

  public int getRepeatCount() {
    return myData.REPEAT_COUNT;
  }

  public void setRepeatCount(int repeatCount) {
    myData.REPEAT_COUNT = repeatCount;
  }

  public String getRepeatMode() {
    return myData.REPEAT_MODE;
  }

  public void setRepeatMode(String repeatMode) {
    myData.REPEAT_MODE = repeatMode;
  }

  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
    return new JUnitConsoleProperties(this, executor);
  }

  @NotNull
  @Override
  public String getFrameworkPrefix() {
    return "j";
  }

  public static class Data implements Cloneable {
    public String PACKAGE_NAME;
    public String MAIN_CLASS_NAME;
    public String METHOD_NAME;
    private String[] UNIQUE_ID;
    private String[] TAGS;
    public String TEST_OBJECT = TEST_CLASS;
    public String VM_PARAMETERS;
    public String PARAMETERS;
    public String WORKING_DIRECTORY;
    public boolean PASS_PARENT_ENVS = true;
    public TestSearchScope.Wrapper TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();
    private String DIR_NAME;
    private String CATEGORY_NAME;
    private String FORK_MODE = FORK_NONE;
    private int REPEAT_COUNT = 1;
    private String REPEAT_MODE = RepeatCount.ONCE;
    private LinkedHashSet<String> myPattern = new LinkedHashSet<>();
    private Map<String, String> myEnvs = new LinkedHashMap<>();
    private String myChangeList = "All";

    public boolean equals(final Object object) {
      if (!(object instanceof Data)) return false;
      final Data second = (Data)object;
      return Comparing.equal(TEST_OBJECT, second.TEST_OBJECT) &&
             Comparing.equal(getMainClassName(), second.getMainClassName()) &&
             Comparing.equal(getPackageName(), second.getPackageName()) &&
             Comparing.equal(getMethodNameWithSignature(), second.getMethodNameWithSignature()) &&
             Comparing.equal(getWorkingDirectory(), second.getWorkingDirectory()) &&
             Comparing.equal(VM_PARAMETERS, second.VM_PARAMETERS) &&
             Comparing.equal(PARAMETERS, second.PARAMETERS) &&
             Comparing.equal(myPattern, second.myPattern) &&
             Comparing.equal(FORK_MODE, second.FORK_MODE) &&
             Comparing.equal(DIR_NAME, second.DIR_NAME) &&
             Comparing.equal(CATEGORY_NAME, second.CATEGORY_NAME) &&
             Comparing.equal(UNIQUE_ID, second.UNIQUE_ID) &&
             Comparing.equal(TAGS, second.TAGS) &&
             Comparing.equal(REPEAT_MODE, second.REPEAT_MODE) &&
             REPEAT_COUNT == second.REPEAT_COUNT;
    }

    public int hashCode() {
      return Comparing.hashcode(TEST_OBJECT) ^
             Comparing.hashcode(getMainClassName()) ^
             Comparing.hashcode(getPackageName()) ^
             Comparing.hashcode(getMethodNameWithSignature()) ^
             Comparing.hashcode(getWorkingDirectory()) ^
             Comparing.hashcode(VM_PARAMETERS) ^
             Comparing.hashcode(PARAMETERS) ^
             Comparing.hashcode(myPattern) ^
             Comparing.hashcode(FORK_MODE) ^
             Comparing.hashcode(DIR_NAME) ^
             Comparing.hashcode(CATEGORY_NAME) ^
             Comparing.hashcode(UNIQUE_ID) ^
             Comparing.hashcode(TAGS) ^
             Comparing.hashcode(REPEAT_MODE) ^
             Comparing.hashcode(REPEAT_COUNT);
    }

    public TestSearchScope getScope() {
      return TEST_SEARCH_SCOPE.getScope();
    }

    public void setScope(final TestSearchScope scope) {
      TEST_SEARCH_SCOPE.setScope(scope);
    }

    @Override
    public Data clone() {
      try {
        Data data = (Data)super.clone();
        data.TEST_SEARCH_SCOPE = new TestSearchScope.Wrapper();
        data.setScope(getScope());
        data.myEnvs = new LinkedHashMap<>(myEnvs);
        return data;
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    public String getVMParameters() {
      return VM_PARAMETERS;
    }

    public void setVMParameters(String value) {
      VM_PARAMETERS = value;
    }

    public String getProgramParameters() {
      return PARAMETERS;
    }

    public void setProgramParameters(String value) {
      PARAMETERS = value;
    }

    public String getWorkingDirectory() {
      return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
    }

    public void setWorkingDirectory(String value) {
      WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
    }

    public void setUniqueIds(String... uniqueId) {
      UNIQUE_ID = uniqueId;
    }

    public String[] getUniqueIds() {
      return UNIQUE_ID;
    }

    public Module setTestMethod(final Location<PsiMethod> methodLocation) {
      final PsiMethod method = methodLocation.getPsiElement();
      METHOD_NAME = getMethodPresentation(method);
      TEST_OBJECT = TEST_METHOD;
      return setMainClass(methodLocation instanceof MethodLocation ? ((MethodLocation)methodLocation).getContainingClass() : method.getContainingClass());
    }

    public String[] getTags() {
      return TAGS;
    }

    public void setTags(String[] tags) {
      TAGS = tags;
    }

    public static String getMethodPresentation(PsiMethod method) {
      if (method.getParameterList().getParametersCount() > 0 && MetaAnnotationUtil.isMetaAnnotated(method, JUnitUtil.TEST5_ANNOTATIONS)) {
        return method.getName() + "(" + ClassUtil.getVMParametersMethodSignature(method) + ")";
      }
      else {
        return method.getName();
      }
    }

    public String getGeneratedName(final JavaRunConfigurationModule configurationModule) {
      if (TEST_PACKAGE.equals(TEST_OBJECT) || TEST_DIRECTORY.equals(TEST_OBJECT)) {
        if (TEST_SEARCH_SCOPE.getScope() == TestSearchScope.WHOLE_PROJECT) {
          return ExecutionBundle.message("default.junit.config.name.whole.project");
        }
        final String moduleName = TEST_SEARCH_SCOPE.getScope() == TestSearchScope.WHOLE_PROJECT ? "" : configurationModule.getModuleName();
        final String packageName = TEST_PACKAGE.equals(TEST_OBJECT) ? getPackageName() : StringUtil.getShortName(getDirName(), '/');
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
        final int size = myPattern.size();
        if (size == 0) return "Temp suite";
        String fqName = myPattern.iterator().next();
        String firstName =
          fqName.contains("*") ? fqName
                               : StringUtil.getShortName(fqName.contains("(") ? StringUtil.getPackageName(fqName, '(') : fqName);
        return firstName + (size > 1 ? " and " + (size - 1) + " more" : "");
      }
      if (TEST_CATEGORY.equals(TEST_OBJECT)) {
        return "@Category(" + (StringUtil.isEmpty(CATEGORY_NAME) ? "Invalid" : CATEGORY_NAME) + ")";
      }
      if (TEST_UNIQUE_ID.equals(TEST_OBJECT)) {
        return UNIQUE_ID != null ? StringUtil.join(UNIQUE_ID, " ") : "Temp suite";
      }
      if (TEST_TAGS.equals(TEST_OBJECT)) {
        return TAGS != null ? "Tags (" + StringUtil.join(TAGS, " ") + ")" : "Temp suite";
      }
      final String className = JavaExecutionUtil.getPresentableClassName(getMainClassName());
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
      String signature = getMethodNameWithSignature();
      int paramsIdx = signature.lastIndexOf("(");
      return paramsIdx > -1 ? signature.substring(0, paramsIdx) : signature;
    }

    public String getMethodNameWithSignature() {
      return METHOD_NAME != null ? METHOD_NAME : "";
    }

    public String getDirName() {
      return DIR_NAME != null ? DIR_NAME : "";
    }

    public void setDirName(String dirName) {
      DIR_NAME = dirName;
    }

    public Set<String> getPatterns() {
      return myPattern;
    }

    public void setPatterns(LinkedHashSet<String> pattern) {
      myPattern = pattern;
    }

    public String getPatternPresentation() {
      final List<String> enabledTests = new ArrayList<>();
      for (String pattern : myPattern) {
        enabledTests.add(pattern);
      }
      return StringUtil.join(enabledTests, "||");
    }

    public TestObject getTestObject(@NotNull JUnitConfiguration configuration) {
      final ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build();
      final TestObject testObject = TestObject.fromString(TEST_OBJECT, configuration, environment);
      return testObject == null ? new UnknownTestTarget(configuration, environment) : testObject;
    }

    public Module setMainClass(final PsiClass testClass) {
      MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(testClass);
      PsiPackage containingPackage = JUnitUtil.getContainingPackage(testClass);
      PACKAGE_NAME = containingPackage != null ? containingPackage.getQualifiedName() : "";
      return JavaExecutionUtil.findModule(testClass);
    }

    public Map<String, String> getEnvs() {
      return myEnvs;
    }

    public void setEnvs(final Map<String, String> envs) {
      myEnvs = envs;
    }

    public String getCategory() {
      return CATEGORY_NAME != null ? CATEGORY_NAME : "";
    }

    public void setCategoryName(String categoryName) {
      CATEGORY_NAME = categoryName;
    }

    public String getChangeList() {
      return myChangeList;
    }

    public void setChangeList(String changeList) {
      myChangeList = changeList;
    }
  }

}
