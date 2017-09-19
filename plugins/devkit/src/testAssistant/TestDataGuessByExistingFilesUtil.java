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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.File;
import java.util.*;

/**
 * There is a possible case that particular test class is not properly configured with test annotations but uses test data files.
 * This class contains utility methods for guessing test data files location and name patterns from existing one.
 *
 * @author Denis Zhdanov
 * @since 5/24/11 2:28 PM
 */
@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
public class TestDataGuessByExistingFilesUtil {

  private TestDataGuessByExistingFilesUtil() {
  }

  /**
   * Tries to guess what test data files match to the given method if it's test method and there are existing test data
   * files for the target test class.
   *
   * @param psiMethod      test method candidate
   * @return            collection of paths to the test data files for the given test if it's possible to guess them;
   *                    {@code null} otherwise
   */
  @Nullable
  static List<String> collectTestDataByExistingFiles(@NotNull PsiMethod psiMethod) {
    TestDataDescriptor descriptor = buildDescriptorFromExistingTestData(psiMethod);
    if (descriptor == null || !descriptor.isComplete()) {
      return null;
    }
    return descriptor.generate();
  }

  static String guessTestDataName(PsiMethod method) {
    String testName = getTestName(method);
    if (testName == null) return null;
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return null;
    int count = 5;
    PsiMethod prev = PsiTreeUtil.getPrevSiblingOfType(method, PsiMethod.class);
    while (prev != null && count-- > 0) {
      String s = getFilePath(prev, testName);
      if (s != null) return s;
      prev = PsiTreeUtil.getPrevSiblingOfType(prev, PsiMethod.class);
    }
    count = 5;
    PsiMethod next = PsiTreeUtil.getNextSiblingOfType(method, PsiMethod.class);
    while (next != null && count-- > 0) {
      String s = getFilePath(next, testName);
      if (s != null) return s;
      next = PsiTreeUtil.getNextSiblingOfType(next, PsiMethod.class);
    }
    return null;
  }

  @Nullable
  private static String getFilePath(PsiMethod psiMethod, String testName) {
    List<String> strings = collectTestDataByExistingFiles(psiMethod);
    if (strings != null && !strings.isEmpty()) {
      String s = strings.get(0);
      return new File(new File(s).getParent(), testName + "." + FileUtilRt.getExtension(new File(s).getName())).getPath();
    }
    return null;
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
    else if (framework.getClass().getName().contains("JUnit4")) {
      return !AnnotationUtil.isAnnotated(method, "org.junit.Test", false);
    }
    return false;
  }

  @NotNull
  public static String getTestName(@NotNull String methodName) {
    return StringUtil.trimStart(methodName, "test");
  }

  @Nullable
  private static TestDataDescriptor buildDescriptorFromExistingTestData(@NotNull final PsiMethod method) {
    final TestDataDescriptor cachedValue = CachedValuesManager.getCachedValue(method,
                                                                              () -> new CachedValueProvider.Result<>(
                                                                                buildDescriptor(method),
                                                                                PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
    return cachedValue == TestDataDescriptor.NOTHING_FOUND ? null : cachedValue;
  }

  private static TestDataDescriptor buildDescriptor(PsiMethod psiMethod) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);
    String testName = getTestName(psiMethod);
    if (testName == null || psiClass == null) return TestDataDescriptor.NOTHING_FOUND;
    return buildDescriptor(testName, psiClass);
  }

  public static List<String> suggestTestDataFiles(@NotNull String testName,
                                                  String testDataPath,
                                                  @NotNull PsiClass psiClass){
    return buildDescriptor(testName, psiClass).generate(testName, testDataPath);
  }

  @NotNull
  private static TestDataDescriptor buildDescriptor(@NotNull String test,
                                                    @NotNull PsiClass psiClass)
  {
    // PhpStorm has tests that use '$' symbol as a file path separator, e.g. 'test$while_stmt$declaration' test
    // stands for '/while_smt/declaration.php' file somewhere in a test data.
    final String possibleFileName = ContainerUtil.getLastItem(StringUtil.split(test, "$"), test);
    assert possibleFileName != null;
    if (possibleFileName.isEmpty()) {
      return TestDataDescriptor.NOTHING_FOUND;
    }

    Project project = psiClass.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    GotoFileModel gotoModel = new GotoFileModel(project);
    final String possibleFilePath = test.replace('$', '/');
    Map<String, TestLocationDescriptor> descriptorsByFileNames = new HashMap<>();
    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiClass));
      final Collection<String> fileNames = getAllFileNames(possibleFileName, gotoModel);
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      indicator.setIndeterminate(false);
      ApplicationManager.getApplication().runReadAction(() -> {
        int fileNamesCount = fileNames.size();
        double currentIndex = 0;
        for (String name : fileNames) {
          ProgressManager.checkCanceled();
          final Object[] elements = gotoModel.getElementsByName(name, false, name);
          for (Object element : elements) {
            if (!(element instanceof PsiFile)) {
              continue;
            }
            final VirtualFile file = ((PsiFile)element).getVirtualFile();
            if (file == null || fileIndex.isInSource(file) && !fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.RESOURCES)) {
              continue;
            }

            final String filePath = file.getPath();
            if (!StringUtil.containsIgnoreCase(filePath, possibleFilePath) && !StringUtil.containsIgnoreCase(filePath, test)) {
              continue;
            }
            final String fileName = PathUtil.getFileName(filePath).toLowerCase();
            int i = fileName.indexOf(possibleFileName.toLowerCase());
            // Skip files that doesn't contain target test name and files that contain digit after target test name fragment.
            // Example: there are tests with names 'testEnter()' and 'testEnter2()' and we don't want test data file 'testEnter2'
            // to be matched to the test 'testEnter()'.
            if (i < 0 || (i + possibleFileName.length() < fileName.length())
                         && Character.isDigit(fileName.charAt(i + possibleFileName.length()))) {
              continue;
            }

            TestLocationDescriptor current = new TestLocationDescriptor();
            current.populate(possibleFileName, file, project, module);
            if (!current.isComplete()) {
              continue;
            }

            TestLocationDescriptor previousDescriptor = descriptorsByFileNames.get(name);
            if (previousDescriptor == null) {
              descriptorsByFileNames.put(name, current);
              continue;
            }
            if (moreRelevantPath(current, previousDescriptor, psiClass)) {
              descriptorsByFileNames.put(name, current);
            }
          }
          indicator.setFraction(++currentIndex / fileNamesCount);
        }
      });
    }, DevKitBundle.message("testdata.searching"), true, project);

    if (!completed) {
      throw new ProcessCanceledException();
    }

    filterDirsFromOtherModules(descriptorsByFileNames);
    return new TestDataDescriptor(descriptorsByFileNames.values(), possibleFileName);
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

  private static void filterDirsFromOtherModules(Map<String, TestLocationDescriptor> descriptorsByFileNames) {
    if (descriptorsByFileNames.size() < 2) {
      return;
    }
    if (descriptorsByFileNames.values().stream().noneMatch(descriptor -> descriptor.isFromCurrentModule)) {
      return;
    }
    descriptorsByFileNames.entrySet().removeIf(e -> !e.getValue().isFromCurrentModule);
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
    String candidateLcDir = candidate.dir.toLowerCase();
    String currentLcDir = current.dir.toLowerCase();
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
        candidateMatchPosition = currentWordsIndex;
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
    public String dir;
    public String filePrefix;
    public String fileSuffix;
    public String ext;
    public boolean startWithLowerCase;
    public boolean isFromCurrentModule;

    public boolean isComplete() {
      return dir != null && filePrefix != null && fileSuffix != null && ext != null;
    }

    public void populate(@NotNull String testName, @NotNull VirtualFile matched, @NotNull Project project, @Nullable Module module) {
      if (testName.isEmpty()) return;
      final String withoutExtension = FileUtil.getNameWithoutExtension(testName);
      boolean excludeExtension = !withoutExtension.equals(testName);
      testName = withoutExtension;
      final String fileName = matched.getNameWithoutExtension();
      int i = fileName.indexOf(testName);
      final char firstChar = testName.charAt(0);
      boolean testNameStartsWithLowerCase = Character.isLowerCase(firstChar);
      if (i < 0) {
        i = fileName.indexOf(
          (testNameStartsWithLowerCase ? Character.toUpperCase(firstChar) : Character.toLowerCase(firstChar)) + testName.substring(1)
        );
        startWithLowerCase = !testNameStartsWithLowerCase;
      }
      else {
        startWithLowerCase = testNameStartsWithLowerCase;
      }
      if (i < 0) {
        return;
      }

      filePrefix = fileName.substring(0, i);
      fileSuffix = fileName.substring(i + testName.length());
      ext = excludeExtension ? "" : matched.getExtension();
      dir = matched.getParent().getPath();
      if (module != null) {
        isFromCurrentModule = module.equals(ModuleUtilCore.findModuleForFile(matched, project));
      }
    }

    @Override
    public int hashCode() {
      int result = dir != null ? dir.hashCode() : 0;
      result = 31 * result + (filePrefix != null ? filePrefix.hashCode() : 0);
      result = 31 * result + (fileSuffix != null ? fileSuffix.hashCode() : 0);
      result = 31 * result + (ext != null ? ext.hashCode() : 0);
      result = 31 * result + (startWithLowerCase ? 1 : 0);
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TestLocationDescriptor that = (TestLocationDescriptor)o;
      if (startWithLowerCase != that.startWithLowerCase) return false;
      if (dir != null ? !dir.equals(that.dir) : that.dir != null) return false;
      if (ext != null ? !ext.equals(that.ext) : that.ext != null) return false;
      if (filePrefix != null ? !filePrefix.equals(that.filePrefix) : that.filePrefix != null) return false;
      if (fileSuffix != null ? !fileSuffix.equals(that.fileSuffix) : that.fileSuffix != null) return false;

      return true;
    }

    @Override
    public String toString() {
      return String.format("%s/%s[...]%s.%s", dir, filePrefix, fileSuffix, ext);
    }
  }

  private static class TestDataDescriptor {
    private static final TestDataDescriptor NOTHING_FOUND = new TestDataDescriptor(Collections.emptyList(), null);

    private final List<TestLocationDescriptor> myDescriptors = new ArrayList<>();
    private final String myTestName;

    TestDataDescriptor(Collection<TestLocationDescriptor> descriptors, String testName) {
      myTestName = testName;
      myDescriptors.addAll(descriptors);
    }

    public boolean isComplete() {
      if (myDescriptors.isEmpty()) {
        return false;
      }

      for (TestLocationDescriptor descriptor : myDescriptors) {
        if (!descriptor.isComplete()) {
          return false;
        }
      }
      return true;
    }

    @NotNull
    public List<String> generate(@NotNull final String testName) {
      return generate(testName, null);
    }

    @NotNull
    public List<String> generate() {
      return generate(myTestName, null);
    }

    @NotNull
    public List<String> generate(@NotNull final String testName, String root) {
      List<String> result = new ArrayList<>();
      if (StringUtil.isEmpty(testName)) {
        return result;
      }
      for (TestLocationDescriptor descriptor : myDescriptors) {
        if (root != null && !root.equals(descriptor.dir)) continue;
        result.add(String.format(
          "%s/%s%c%s%s%s",
          descriptor.dir, descriptor.filePrefix,
          descriptor.startWithLowerCase ? Character.toLowerCase(testName.charAt(0)) : Character.toUpperCase(testName.charAt(0)),
          testName.substring(1), descriptor.fileSuffix, StringUtil.isEmpty(descriptor.ext) ? "" : "." + descriptor.ext
        ));
      }
      return result;
    }

    @Override
    public String toString() {
      return myDescriptors.toString();
    }
  }
}
