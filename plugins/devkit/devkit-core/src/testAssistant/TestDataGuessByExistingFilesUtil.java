// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;

/**
 * There is a possible case that particular test class is not properly configured with test annotations but uses test data files.
 * This class contains utility methods for guessing test data files location and name patterns from existing one.
 *
 * @author Denis Zhdanov
 */
@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
public class TestDataGuessByExistingFilesUtil {
  private static final Logger LOG = Logger.getInstance(TestDataGuessByExistingFilesUtil.class);

  private TestDataGuessByExistingFilesUtil() {
  }

  /**
   * Tries to guess what test data files match to the given method if it's test method and there are existing test data
   * files for the target test class.
   *
   * @param psiMethod test method candidate
   * @param testDataPath test data path if present (e.g. obtained from @TestDataPath annotation value)
   * @return List of existing test data files for the given test if it's possible to guess them; empty List otherwise
   */
  @NotNull
  static List<TestDataFile> collectTestDataByExistingFiles(@NotNull PsiMethod psiMethod, @Nullable String testDataPath) {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode() && application.isHeadlessEnvironment()) {
      // shouldn't be invoked under these conditions anyway, just for additional safety
      LOG.warn("Collecting testdata by existing files called in headless environment and not-unit testing mode");
      return Collections.emptyList();
    }

    return ReadAction.compute(() -> buildDescriptorFromExistingTestData(psiMethod, testDataPath).restoreFiles());
  }

  static List<TestDataFile> guessTestDataName(PsiMethod method) {
    String testName = getTestName(method);
    if (testName == null) return null;
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return null;
    String testDataBasePath = TestDataLineMarkerProvider.getTestDataBasePath(psiClass);
    int count = 5;
    PsiMethod prev = PsiTreeUtil.getPrevSiblingOfType(method, PsiMethod.class);
    while (prev != null && count-- > 0) {
      List<TestDataFile> testData = guessTestDataBySiblingTest(prev, testDataBasePath, testName);
      if (!testData.isEmpty()) return testData;
      prev = PsiTreeUtil.getPrevSiblingOfType(prev, PsiMethod.class);
    }
    count = 5;
    PsiMethod next = PsiTreeUtil.getNextSiblingOfType(method, PsiMethod.class);
    while (next != null && count-- > 0) {
      List<TestDataFile> testData = guessTestDataBySiblingTest(next, testDataBasePath, testName);
      if (!testData.isEmpty()) return testData;
      next = PsiTreeUtil.getNextSiblingOfType(next, PsiMethod.class);
    }
    return null;
  }

  @NotNull
  private static List<TestDataFile> guessTestDataBySiblingTest(PsiMethod psiMethod, String testDataBasePath, String testName) {
    return buildDescriptorFromExistingTestData(psiMethod, testDataBasePath).generateByTemplates(testName, null);
  }

  @Nullable
  private static String getTestName(@NotNull PsiMethod method) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    TestFramework framework = TestFrameworks.detectFramework(psiClass);

    if (framework == null || isUtilityMethod(method, psiClass, framework)) {
      return null;
    }

    return getTestName(method.getName());
  }

  private static boolean isUtilityMethod(@NotNull PsiMethod method, @NotNull PsiClass psiClass, @NotNull TestFramework framework) {
    if (method == framework.findSetUpMethod(psiClass) || method == framework.findTearDownMethod(psiClass)) {
      return true;
    }

    // JUnit3
    if (framework.getClass().getName().contains("JUnit3")) {
      return !method.getName().startsWith("test");
    }

    // JUnit4
    if (framework.getClass().getName().contains("JUnit4")) {
      return !AnnotationUtil.isAnnotated(method, "org.junit.Test", 0);
    }

    return false;
  }

  @NotNull
  public static String getTestName(@NotNull String methodName) {
    return StringUtil.trimStart(methodName, "test");
  }

  @NotNull
  private static TestDataDescriptor buildDescriptorFromExistingTestData(@NotNull PsiMethod method, @Nullable String testDataPath) {
    return CachedValuesManager.getCachedValue(method,
                                              () -> new CachedValueProvider.Result<>(
                                                                                buildDescriptor(method, testDataPath),
                                                                                PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  @NotNull
  private static TestDataDescriptor buildDescriptor(@NotNull PsiMethod psiMethod, @Nullable String testDataPath) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
    String testName = getTestName(psiMethod);
    if (testName == null || psiClass == null) return TestDataDescriptor.NOTHING_FOUND;
    return buildDescriptor(testName, psiClass, testDataPath);
  }

  public static List<TestDataFile> suggestTestDataFiles(@NotNull String testName,
                                                        String testDataPath,
                                                        @NotNull PsiClass psiClass) {
    return buildDescriptor(testName, psiClass, testDataPath).restoreFiles();
  }

  @NotNull
  private static TestDataDescriptor buildDescriptor(@NotNull String test,
                                                    @NotNull PsiClass psiClass,
                                                    @Nullable String testDataPath) {
    String normalizedTestDataPath = testDataPath == null ? null : StringUtil.trimEnd(StringUtil.trimEnd(testDataPath, "/"), "\\");

    // PhpStorm has tests that use '$' symbol as a file path separator, e.g. 'test$while_stmt$declaration' test
    // stands for '/while_smt/declaration.php' file somewhere in a test data.
    String possibleFileName = ContainerUtil.getLastItem(StringUtil.split(test, "$"), test);
    assert possibleFileName != null;
    if (possibleFileName.isEmpty()) {
      return TestDataDescriptor.NOTHING_FOUND;
    }
    Project project = psiClass.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    GotoFileModel gotoModel = new GotoFileModel(project);
    String possibleFilePath = test.replace('$', '/');
    Map<String, List<TestLocationDescriptor>> descriptorsByFileNames = new HashMap<>();
    Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiClass));
    Collection<String> fileNames = getAllFileNames(possibleFileName, gotoModel);
    ProgressIndicator indicator = EmptyProgressIndicator.notNullize(ProgressManager.getInstance().getProgressIndicator());
    indicator.setText("Searching for \'" + test + "\' test data files...");
    indicator.setIndeterminate(false);
    int fileNamesCount = fileNames.size();
    double currentIndex = 0;
    for (String name : fileNames) {
      ProgressManager.checkCanceled();
      Object[] elements = gotoModel.getElementsByName(name, false, name);
      for (Object element : elements) {
        if (!(element instanceof PsiFileSystemItem)) {
          continue;
        }

        PsiFileSystemItem psiFile = (PsiFileSystemItem)element;
        if (normalizedTestDataPath != null) {
          PsiFileSystemItem containingDirectory = psiFile.getParent();
          if (containingDirectory != null) {
            VirtualFile directoryVirtualFile = containingDirectory.getVirtualFile();
            String normalizedDirPath = StringUtil.trimEnd(StringUtil.trimEnd(directoryVirtualFile.getPath(), "/"), "\\");
            if (!normalizedDirPath.startsWith(normalizedTestDataPath)) {
              continue;
            }
          }
        }

        VirtualFile file = psiFile.getVirtualFile();
        if (file == null || fileIndex.isInSource(file) && !fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.RESOURCES)) {
          continue;
        }

        String filePath = file.getPath();
        if (!StringUtil.containsIgnoreCase(filePath, possibleFilePath) && !StringUtil.containsIgnoreCase(filePath, test)) {
          continue;
        }
        String fileName = PathUtil.getFileName(filePath).toLowerCase();
        int i = fileName.indexOf(possibleFileName.toLowerCase());
        // Skip files that doesn't contain target test name and files that contain digit after target test name fragment.
        // Example: there are tests with names 'testEnter()' and 'testEnter2()' and we don't want test data file 'testEnter2'
        // to be matched to the test 'testEnter()'.
        if (i < 0 || (i + possibleFileName.length() < fileName.length())
                     && Character.isDigit(fileName.charAt(i + possibleFileName.length()))) {
          continue;
        }

        List<TestLocationDescriptor> currentDescriptors = new SmartList<>();
        VfsUtilCore.processFilesRecursively(file, f -> {
          if (f.isDirectory()) return true;
          TestLocationDescriptor current = TestLocationDescriptor.create(possibleFileName, f, project, module);
          if (current != null) {
            currentDescriptors.add(current);
          }
          return true;
        });

        if (!currentDescriptors.isEmpty()) {
          List<TestLocationDescriptor> previousDescriptors = descriptorsByFileNames.get(name);
          if (previousDescriptors == null) {
            descriptorsByFileNames.put(name, currentDescriptors);
            continue;
          }
          if (moreRelevantPath(currentDescriptors.get(0), previousDescriptors.get(0), psiClass)) {
            descriptorsByFileNames.put(name, currentDescriptors);
          }
        }
      }
      indicator.setFraction(++currentIndex / fileNamesCount);
    }

    List<TestLocationDescriptor> descriptors = ContainerUtil.flatten(descriptorsByFileNames.values());
    filterDirsFromOtherModules(descriptors);
    return new TestDataDescriptor(descriptors);
  }

  private static Collection<String> getAllFileNames(final String testName, final GotoFileModel model) {
    CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<String>() {
      @Override
      public boolean accept(String name) {
        ProgressManager.checkCanceled();
        return StringUtil.containsIgnoreCase(name, testName);
      }
    };
    model.processNames(processor, false);
    return processor.getResults();
  }

  private static void filterDirsFromOtherModules(List<TestLocationDescriptor> descriptorsByFileNames) {
    if (descriptorsByFileNames.size() < 2) {
      return;
    }
    if (descriptorsByFileNames.stream().noneMatch(descriptor -> descriptor.isFromCurrentModule)) {
      return;
    }
    descriptorsByFileNames.removeIf(d -> !d.isFromCurrentModule);
  }

  @Nullable
  private static String getSimpleClassName(@NotNull PsiClass psiClass) {
    String result = psiClass.getQualifiedName();
    if (result == null) {
      return null;
    }
    result = StringUtil.trimEnd(result, "Test");
    int i = result.lastIndexOf('.');
    if (i >= 0) {
      result = result.substring(i + 1);
    }
    return result;
  }

  private static boolean moreRelevantPath(@NotNull TestLocationDescriptor candidate,
                                          @NotNull TestLocationDescriptor current,
                                          @NotNull PsiClass psiClass)
  {
    final String className = psiClass.getQualifiedName();
    if (className == null) {
      return false;
    }

    boolean candidateMatched;
    boolean currentMatched;

    // By package.
    int lastDotIndex = className.lastIndexOf(".");
    String candidateLcDir = candidate.pathPrefix.toLowerCase();
    String currentLcDir = current.pathPrefix.toLowerCase();
    if (lastDotIndex >= 0) {
      String packageAsPath = className.substring(0, lastDotIndex).replace('.', '/').toLowerCase();
      candidateMatched = candidateLcDir.contains(packageAsPath);
      currentMatched = currentLcDir.contains(packageAsPath);
      if (candidateMatched ^ currentMatched) {
        return candidateMatched;
      }
    }

    // By class name.
    String simpleName = getSimpleClassName(psiClass);
    if (simpleName == null) {
      return false;
    }
    String pattern = simpleName.toLowerCase();
    candidateMatched = candidateLcDir.contains(pattern);
    currentMatched = currentLcDir.contains(pattern);
    if (candidateMatched ^ currentMatched) {
      return candidateMatched;
    }

    // By class name words and their position. More words + greater position = better.
    String[] words = NameUtil.nameToWords(simpleName);
    int candidateWordsMatched = 0;
    int currentWordsMatched = 0;
    int candidateMatchPosition = -1;
    int currentMatchPosition = -1;

    StringBuilder currentNameSubstringSb = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      currentNameSubstringSb.append(words[i]);
      String currentNameLcSubstring = currentNameSubstringSb.toString().toLowerCase();

      int candidateWordsIndex = candidateLcDir.lastIndexOf(currentNameLcSubstring);
      if (candidateWordsIndex > 0) {
        candidateWordsMatched = i + 1;
        candidateMatchPosition = candidateWordsIndex;
      }

      int currentWordsIndex = currentLcDir.lastIndexOf(currentNameLcSubstring);
      if (currentWordsIndex > 0) {
        currentWordsMatched = i + 1;
        currentMatchPosition = currentWordsIndex;
      }

      if (candidateWordsMatched != currentWordsMatched) {
        break; // no need to continue
      }
    }

    if (candidateWordsMatched != currentWordsMatched) {
      return candidateWordsMatched > currentWordsMatched;
    }
    return candidateMatchPosition > currentMatchPosition;
  }

  private static class TestLocationDescriptor {
    final String pathPrefix;
    final String pathSuffix;
    final boolean startWithLowerCase;
    final boolean isFromCurrentModule;
    final int matchedVFileId;

    private TestLocationDescriptor(String pathPrefix, String pathSuffix, boolean startWithLowerCase, boolean isFromCurrentModule, int id) {
      this.pathPrefix = pathPrefix;
      this.pathSuffix = pathSuffix;
      this.startWithLowerCase = startWithLowerCase;
      this.isFromCurrentModule = isFromCurrentModule;
      matchedVFileId = id;
    }

    static TestLocationDescriptor create(@NotNull String testName, @NotNull VirtualFile matched, @NotNull Project project, @Nullable Module module) {
      if (testName.isEmpty()) return null;

      String path = matched.getPath();
      int idx = StringUtil.indexOf(path, testName);
      boolean capitalized = StringUtil.isCapitalized(testName);
      boolean startWithLowerCase;
      if (idx < 0) {
        testName = capitalized ? StringUtil.decapitalize(testName) : StringUtil.capitalize(testName);
        idx = StringUtil.indexOf(path, testName);
        if (idx < 0) return null;
        startWithLowerCase = capitalized;
      } else {
        startWithLowerCase = !capitalized;
      }

      String pathPrefix = path.substring(0, idx);
      String pathSuffix = path.substring(idx + testName.length());
      boolean isFromCurrentModule = false;
      if (module != null) {
        isFromCurrentModule = module.equals(ModuleUtilCore.findModuleForFile(matched, project));
      }
      int matchedVFileId = ((VirtualFileWithId)matched).getId();
      return new TestLocationDescriptor(pathPrefix, pathSuffix, startWithLowerCase, isFromCurrentModule, matchedVFileId);
    }

    @Override
    public int hashCode() {
      int result = 0;
      result = 31 * result + (pathPrefix != null ? pathPrefix.hashCode() : 0);
      result = 31 * result + (pathSuffix != null ? pathSuffix.hashCode() : 0);
      result = 31 * result + (startWithLowerCase ? 1 : 0);
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TestLocationDescriptor that = (TestLocationDescriptor)o;
      if (startWithLowerCase != that.startWithLowerCase) return false;
      if (!Objects.equals(pathPrefix, that.pathPrefix)) return false;
      if (!Objects.equals(pathSuffix, that.pathSuffix)) return false;

      return true;
    }

    @Override
    public String toString() {
      return String.format("%s[...]%s", pathPrefix, pathSuffix);
    }
  }

  private static class TestDataDescriptor {
    private static final TestDataDescriptor NOTHING_FOUND = new TestDataDescriptor(Collections.emptyList());

    private final List<TestLocationDescriptor> myDescriptors = new ArrayList<>();

    TestDataDescriptor(Collection<TestLocationDescriptor> descriptors) {
      myDescriptors.addAll(descriptors);
    }

    @NotNull
    public List<TestDataFile> restoreFiles() {
      return ContainerUtil.mapNotNull(myDescriptors, d -> {
        VirtualFile file = ReadAction.compute(() -> VirtualFileManager.getInstance().findFileById(d.matchedVFileId));
        return file == null ? null : new TestDataFile.Existing(file);
      });
    }

    @NotNull
    public List<TestDataFile> generateByTemplates(@NotNull String testName, @Nullable String root) {
      List<TestDataFile> result = new ArrayList<>();
      if (StringUtil.isEmpty(testName)) {
        return result;
      }
      for (TestLocationDescriptor descriptor : myDescriptors) {
        if (root != null && !descriptor.pathPrefix.startsWith(root)) continue;
        result.add(new TestDataFile.NonExisting(descriptor.pathPrefix + (descriptor.startWithLowerCase
                                                                         ? StringUtil.decapitalize(testName)
                                                                         : StringUtil.capitalize(testName)) + descriptor.pathSuffix));
      }
      return result;
    }

    @Override
    public String toString() {
      return myDescriptors.toString();
    }
  }
}
