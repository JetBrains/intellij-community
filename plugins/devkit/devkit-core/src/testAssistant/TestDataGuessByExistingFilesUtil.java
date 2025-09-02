// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

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
import com.intellij.psi.*;
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
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;

/**
 * There is a possible case that particular test class is not properly configured with test annotations but uses test data files.
 * This class contains utility methods for guessing test data files location and name patterns from existing one.
 */
@ApiStatus.Internal
public final class TestDataGuessByExistingFilesUtil {
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
  @VisibleForTesting
  public static @NotNull List<TestDataFile> collectTestDataByExistingFiles(@NotNull PsiMethod psiMethod, @Nullable String testDataPath) {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode() && application.isHeadlessEnvironment()) {
      // shouldn't be invoked under these conditions anyway, just for additional safety
      LOG.warn("Collecting testdata by existing files called in headless environment and not-unit testing mode");
      return Collections.emptyList();
    }

    return ReadAction.compute(() -> buildDescriptorFromExistingTestData(psiMethod, testDataPath).restoreFiles());
  }

  @VisibleForTesting
  public static @NotNull List<TestDataFile> guessTestDataName(PsiMethod method) {
    String testName = getTestName(method);
    if (testName == null) return Collections.emptyList();
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return Collections.emptyList();
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
    return Collections.emptyList();
  }

  private static @NotNull List<TestDataFile> guessTestDataBySiblingTest(PsiMethod psiMethod, String testDataBasePath, String testName) {
    return buildDescriptorFromExistingTestData(psiMethod, testDataBasePath).generateByTemplates(testName, null);
  }

  private static @Nullable String getTestName(@NotNull PsiMethod method) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    TestFramework framework = TestFrameworks.detectFramework(psiClass);

    if (framework == null || !framework.isTestMethod(method)) {
      return null;
    }

    return getTestName(method.getName());
  }

  public static @NotNull String getTestName(@NotNull String methodName) {
    return StringUtil.trimStart(methodName, "test");
  }

  private static @NotNull TestDataDescriptor buildDescriptorFromExistingTestData(@NotNull PsiMethod method, @Nullable String testDataPath) {
    return CachedValuesManager.getCachedValue(method,
                                              () -> new CachedValueProvider.Result<>(
                                                buildDescriptor(method, testDataPath),
                                                PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @NotNull TestDataDescriptor buildDescriptor(@NotNull PsiMethod psiMethod, @Nullable String testDataPath) {
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

  private static @NotNull TestDataDescriptor buildDescriptor(@NotNull String testName,
                                                             @NotNull PsiClass psiClass,
                                                             @Nullable String testDataPath) {
    String normalizedTestDataPath = testDataPath == null ? null : StringUtil.trimEnd(StringUtil.trimEnd(testDataPath, "/"), "\\");

    // PhpStorm has tests that use '$' symbol as a file path separator, e.g. 'test$while_stmt$declaration' test
    // stands for '/while_smt/declaration.php' file somewhere in a test data.
    String possibleFileName = ContainerUtil.getLastItem(StringUtil.split(testName, "$"), testName);
    assert possibleFileName != null;
    if (possibleFileName.isEmpty()) {
      return TestDataDescriptor.NOTHING_FOUND;
    }
    Project project = psiClass.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    GotoFileModel gotoModel = new GotoFileModel(project);
    String possibleFilePath = testName.replace('$', '/');
    Map<String, List<TestLocationDescriptor>> descriptorsByFileNames = new HashMap<>();
    Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiClass));
    Collection<String> fileNames = getAllFileNames(possibleFileName, gotoModel);
    ProgressIndicator indicator = EmptyProgressIndicator.notNullize(ProgressManager.getInstance().getProgressIndicator());
    indicator.setText(DevKitBundle.message("testdata.progress.text.searching.for.test.data.files", testName));
    indicator.setIndeterminate(false);
    int fileNamesCount = fileNames.size();
    double currentIndex = 0;
    for (String name : fileNames) {
      ProgressManager.checkCanceled();
      Object[] elements = gotoModel.getElementsByName(name, false, name);
      for (Object element : elements) {
        if (!(element instanceof PsiFileSystemItem psiFile)) {
          continue;
        }

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
        if (!StringUtil.containsIgnoreCase(filePath, possibleFilePath) && !StringUtil.containsIgnoreCase(filePath, testName)) {
          continue;
        }
        String fileName = StringUtil.toLowerCase(PathUtil.getFileName(filePath));
        int i = fileName.indexOf(StringUtil.toLowerCase(possibleFileName));
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
    descriptors = filterDirsFromOtherModules(descriptors);
    return new TestDataDescriptor(descriptors);
  }

  private static Collection<String> getAllFileNames(final String testName, final GotoFileModel model) {
    CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<>() {
      @Override
      public boolean accept(String name) {
        ProgressManager.checkCanceled();
        return StringUtil.containsIgnoreCase(name, testName);
      }
    };
    model.processNames(processor, FindSymbolParameters.simple(model.getProject(), false));
    return processor.getResults();
  }

  private static @Unmodifiable @NotNull List<TestLocationDescriptor> filterDirsFromOtherModules(@Unmodifiable List<TestLocationDescriptor> descriptorsByFileNames) {
    if (descriptorsByFileNames.size() < 2) {
      return descriptorsByFileNames;
    }
    if (!ContainerUtil.exists(descriptorsByFileNames, descriptor -> descriptor.isFromCurrentModule)) {
      return descriptorsByFileNames;
    }
    return ContainerUtil.filter(descriptorsByFileNames, d -> d.isFromCurrentModule);
  }

  private static @Nullable String getSimpleClassName(@NotNull PsiClass psiClass) {
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
    String candidateLcDir = StringUtil.toLowerCase(candidate.pathPrefix);
    String currentLcDir = StringUtil.toLowerCase(current.pathPrefix);
    if (lastDotIndex >= 0) {
      String packageAsPath = StringUtil.toLowerCase(className.substring(0, lastDotIndex).replace('.', '/'));
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
    String pattern = StringUtil.toLowerCase(simpleName);
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
      String currentNameLcSubstring = StringUtil.toLowerCase(currentNameSubstringSb.toString());

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

  private static final class TestLocationDescriptor {
    final String pathPrefix;
    final String pathSuffix;
    final boolean startWithLowerCase;
    final boolean isFromCurrentModule;
    private final SmartPsiElementPointer<PsiFile> filePointer;

    private TestLocationDescriptor(String pathPrefix, String pathSuffix, boolean startWithLowerCase, boolean isFromCurrentModule, SmartPsiElementPointer<PsiFile> filePointer) {
      this.pathPrefix = pathPrefix;
      this.pathSuffix = pathSuffix;
      this.startWithLowerCase = startWithLowerCase;
      this.isFromCurrentModule = isFromCurrentModule;
      this.filePointer = filePointer;
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
      PsiFile file = PsiManager.getInstance(project).findFile(matched);
      if (file == null) return null;
      return new TestLocationDescriptor(pathPrefix,
                                        pathSuffix,
                                        startWithLowerCase,
                                        isFromCurrentModule,
                                        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(file));
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
      return String.format("%s[...]%s", pathPrefix, pathSuffix); //NON-NLS
    }
  }

  private static final class TestDataDescriptor {
    private static final TestDataDescriptor NOTHING_FOUND = new TestDataDescriptor(Collections.emptyList());

    private final List<TestLocationDescriptor> myDescriptors = new ArrayList<>();

    TestDataDescriptor(@Unmodifiable Collection<TestLocationDescriptor> descriptors) {
      myDescriptors.addAll(descriptors);
    }

    public @NotNull @Unmodifiable List<TestDataFile> restoreFiles() {
      return ContainerUtil.mapNotNull(myDescriptors, d -> {
        PsiFile file = ReadAction.compute(() -> d.filePointer.getElement());
        return file == null ? null : new TestDataFile.Existing(file.getVirtualFile());
      });
    }

    public @NotNull List<TestDataFile> generateByTemplates(@NotNull String testName, @Nullable String root) {
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
