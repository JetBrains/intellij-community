/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
   * @param method      test method candidate
   * @return            collection of paths to the test data files for the given test if it's possible to guess them;
   *                    <code>null</code> otherwise
   */
  @Nullable
  static List<String> collectTestDataByExistingFiles(@NotNull PsiMethod method) {
    if (getTestName(method) == null) {
      return null;
    }
    PsiFile psiFile = getParent(method, PsiFile.class);
    if (psiFile == null) {
      return null;
    }
    return collectTestDataByExistingFiles(psiFile, getTestName(method.getName()));
  }

  @Nullable
  private static <T extends PsiElement> T getParent(@NotNull PsiElement element, Class<T> clazz) {
    for (PsiElement e = element; e != null; e = e.getParent()) {
      if (clazz.isAssignableFrom(e.getClass())) {
        return clazz.cast(e);
      }
    }
    return null;
  }

  @Nullable
  static List<String> collectTestDataByExistingFiles(@NotNull PsiFile psiFile, @NotNull String testName) {
    TestDataDescriptor descriptor = buildDescriptorFromExistingTestData(psiFile);
    if (descriptor == null || !descriptor.isComplete()) {
      return null;
    }

    return descriptor.generate(testName);
  }

  @Nullable
  private static String getTestName(@NotNull PsiMethod method) {
    final PsiClass psiClass = getParent(method, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    TestFramework framework = null;
    for (TestFramework each : frameworks) {
      if (each.isTestClass(psiClass)) {
        framework = each;
        break;
      }
    }

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
  private static TestDataDescriptor buildDescriptorFromExistingTestData(@NotNull PsiFile file) {
    final PsiClass psiClass = PsiTreeUtil.getChildOfType(file, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    final TestDataDescriptor cachedValue = CachedValuesManager.getCachedValue(psiClass, new CachedValueProvider<TestDataDescriptor>() {
      @Nullable
      @Override
      public Result<TestDataDescriptor> compute() {
        return new Result<TestDataDescriptor>(buildDescriptor(psiClass), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }
    });
    if (cachedValue == TestDataDescriptor.NOTHING_FOUND) {
      return null;
    }
    return cachedValue;
  }

  private static TestDataDescriptor buildDescriptor(PsiClass psiClass) {
    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    TestFramework framework = null;
    for (TestFramework each : frameworks) {
      if (each.isTestClass(psiClass)) {
        framework = each;
        break;
      }
    }
    if (framework == null) {
      return TestDataDescriptor.NOTHING_FOUND;
    }

    final PsiElement setUpMethod = framework.findSetUpMethod(psiClass);
    final PsiElement tearDownMethod = framework.findTearDownMethod(psiClass);
    List<String> testNames = new ArrayList<String>();
    for (PsiMethod method : psiClass.getMethods()) {
      final String name = getTestName(method.getName());
      if (StringUtil.isEmpty(name) || method == setUpMethod || method == tearDownMethod || name.equals(psiClass.getName())
          || isUtilityMethod(method, psiClass, framework))
      {
        continue;
      }
      testNames.add(name);
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiClass.getProject()).getFileIndex();
    final TestDataDescriptor descriptor = buildDescriptor(fileIndex, testNames, psiClass);
    if (isClassWithoutTestData(descriptor, testNames, psiClass)) {
      return TestDataDescriptor.NOTHING_FOUND;
    }
    return descriptor;
  }

  private static boolean isClassWithoutTestData(@NotNull TestDataDescriptor descriptor, @NotNull List<String> testNames,
                                                @NotNull PsiClass psiClass) {
    if (testNames.size() <= 1) {
      // There is a possible case that the test class is just created.
      return false;
    }

    if (!descriptor.isComplete()) {
      return true;
    }
    
    boolean tooGenericNames = true;
    genericNamesLoop:
    for (String testName : testNames) {
      for (int i = 0; i < testName.length(); i++) {
        if (!Character.isDigit(testName.charAt(i))) {
          tooGenericNames = false;
          break genericNamesLoop;
        }
      }
    }

    final String simpleClassName = getSimpleClassName(psiClass);
    if (tooGenericNames 
        && (simpleClassName == null || !descriptor.myDescriptors.get(0).dir.toLowerCase().contains(simpleClassName.toLowerCase())))
    {
      return true;
    }
    
    // We assume that test has test data if max(2; half of tests) tests already have test data.
    int toMatch = Math.max(2, testNames.size() / 2);
    for (String testName : testNames) {
      if (toMatch <= 0) {
        return false;
      }
      final List<String> testDataFiles = descriptor.generate(testName);
      for (String path : testDataFiles) {
        if (new File(path).isFile()) {
          // There is a possible case that particular test has only one test data file though the others have
          // two (e.g. during testing caret position at virtual space).
          toMatch--;
          break;
        }
      }
    }
    
    return toMatch > 0;
  }
  
  //@NotNull
  //private static Collection<VirtualFile> getMatchedFiles(@NotNull final Project project, @NotNull final String testName) {
  //  final List<VirtualFile> result = new ArrayList<VirtualFile>();
  //  final char c = testName.charAt(0);
  //  final String testNameWithDifferentRegister =
  //    (Character.isLowerCase(c) ? Character.toUpperCase(c) : Character.toLowerCase(c)) + testName.substring(1);
  //  final GlobalSearchScope scope = ProjectScope.getProjectScope(project);
  //  FileBasedIndex.getInstance().processAllKeys(FilenameIndex.NAME, new Processor<String>() {
  //    @Override
  //    public boolean process(String s) {
  //      if (!s.contains(testName) && !s.contains(testNameWithDifferentRegister)) {
  //        return true;
  //      }
  //
  //      final NavigationItem[] items = FilenameIndex.getFilesByName(project, s, scope);
  //      if (items != null) {
  //        for (NavigationItem item : items) {
  //          if (item instanceof PsiFile) {
  //            result.add(((PsiFile)item).getVirtualFile());
  //          }
  //        }
  //      }
  //      return true;
  //    }
  //  }, project);
  //  return result;
  //}

  public static List<String> suggestTestDataFiles(@NotNull ProjectFileIndex fileIndex,
                                                  @NotNull String testName,
                                                  String testDataPath, 
                                                  @NotNull PsiClass psiClass){
    return buildDescriptor(fileIndex, Collections.singletonList(testName), psiClass).generate(testName, testDataPath);
  }

    
  @NotNull
  private static TestDataDescriptor buildDescriptor(@NotNull ProjectFileIndex fileIndex,
                                                    @NotNull Collection<String> testNames,
                                                    @NotNull PsiClass psiClass)
  {
    GotoFileModel gotoModel = new GotoFileModel(psiClass.getProject());
    Set<String> testNamesLowerCase = new HashSet<String>();
    for (String testName : testNames) {
      testNamesLowerCase.add(testName.toLowerCase());
    }
    Set<TestLocationDescriptor> descriptors = new HashSet<TestLocationDescriptor>();
    MultiMap<String, String> map = getAllFileNames(testNames, gotoModel);
    for (String name : map.keySet()) {
      ProgressManager.checkCanceled();
      boolean currentNameProcessed = false;
      for (String test : map.get(name)) {
        final Object[] elements = gotoModel.getElementsByName(name, false, name);
        for (Object element : elements) {
          if (!(element instanceof PsiFile)) {
            continue;
          }
          final VirtualFile file = ((PsiFile)element).getVirtualFile();
          if (file == null || fileIndex.isInSource(file) && !fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.RESOURCES)) {
            continue;
          }


          final String filePath = PathUtil.getFileName(file.getPath()).toLowerCase();
          int i = filePath.indexOf(test.toLowerCase());
          // Skip files that doesn't contain target test name and files that contain digit after target test name fragment.
          // Example: there are tests with names 'testEnter()' and 'testEnter2()' and we don't want test data file 'testEnter2'
          // to be matched to the test 'testEnter()'.
          if (i < 0 || (i + test.length() < filePath.length())
                       && Character.isDigit(filePath.charAt(i + test.length())))
          {
            continue;
          }

          TestLocationDescriptor current = new TestLocationDescriptor();
          current.populate(test, file);
          if (!current.isComplete()) {
            continue;
          }

          // Handle situations like the one below:
          //     *) test class has tests with names 'testAlignedParameters' and 'testNonAlignedParameters';
          //     *) test data files with the following names present: 'AlignedParameters.java' and 'NonAlignedParameters.java';
          //     *) we're processing the following (test; test data file) pair - ('testAlignedParameters'; 'NonAlignedParameters.java');
          // We don't want to store descriptor with file prefix 'Non' here.
          // The same is true for suffixes, e.g. tests like 'testLeaveValidCodeBlock()' and 'testLeaveValidCodeBlockWithEmptyLineAfterIt()'
          String prefixPattern = current.filePrefix.toLowerCase();
          boolean checkPrefix = !StringUtil.isEmpty(prefixPattern);
          String suffixPattern = current.fileSuffix;
          for (TestLocationDescriptor descriptor : descriptors) {
            if (suffixPattern.endsWith(descriptor.fileSuffix)) {
              suffixPattern = suffixPattern.substring(0, suffixPattern.length() - descriptor.fileSuffix.length());
            }
          }
          suffixPattern = suffixPattern.toLowerCase();
          boolean checkSuffix = !StringUtil.isEmpty(suffixPattern);
          boolean skip = false;
          for (String testName : testNamesLowerCase) {
            if (testName.equals(test)) {
              continue;
            }
            if ((checkPrefix && testName.startsWith(prefixPattern)) || (checkSuffix && testName.endsWith(suffixPattern))) {
              skip = true;
              break;
            }
          }
          if (skip) {
            continue;
          }

          currentNameProcessed = true;
          if (descriptors.isEmpty() || (descriptors.iterator().next().dir.equals(current.dir) && !descriptors.contains(current))) {
            descriptors.add(current);
            continue;
          }
          if (moreRelevantPath(current, descriptors, psiClass)) {
            descriptors.clear();
            descriptors.add(current);
          }
        }
        if (currentNameProcessed) {
          break;
        }
      }
    }
    return new TestDataDescriptor(descriptors);
  }

  private static MultiMap<String, String> getAllFileNames(final Collection<String> testNames, final GotoFileModel model) {
    final LinkedMultiMap<String, String> map = new LinkedMultiMap<String, String>();
    model.processNames(new Processor<String>() {
      @Override
      public boolean process(String name) {
        ProgressManager.checkCanceled();
        for (String testName : testNames) {
          if (StringUtil.containsIgnoreCase(name, testName)) {
            map.putValue(name, testName);
          }
        }
        return true;
      }
    }, false);
    return map;
  }

  @Nullable
  private static String getSimpleClassName(@NotNull PsiClass psiClass) {
    String result = psiClass.getQualifiedName();
    if (result == null) {
      return null;
    }
    if (result.endsWith("Test")) {
      result = result.substring(0, result.length() - "Test".length());
    }
    int i = result.lastIndexOf('.');
    if (i >= 0) {
      result = result.substring(i + 1);
    }
    return result;
  }
  
  private static boolean moreRelevantPath(@NotNull TestLocationDescriptor candidate,
                                          @NotNull Set<TestLocationDescriptor> currentDescriptors,
                                          @NotNull PsiClass psiClass)
  {
    final String className = psiClass.getQualifiedName();
    if (className == null) {
      return false;
    }

    final TestLocationDescriptor current = currentDescriptors.iterator().next();
    boolean candidateMatched;
    boolean currentMatched;

    // By package.
    int i = className.lastIndexOf(".");
    if (i >= 0) {
      String packageAsPath = className.substring(0, i).replace('.', '/').toLowerCase();
      candidateMatched = candidate.dir.toLowerCase().contains(packageAsPath);
      currentMatched = current.dir.toLowerCase().contains(packageAsPath);
      if (candidateMatched ^ currentMatched) {
        return candidateMatched;
      }
    }

    // By class name.
    String simpleName = getSimpleClassName(psiClass);
    if (simpleName != null) {
      String pattern = simpleName.toLowerCase();
      candidateMatched = candidate.dir.toLowerCase().contains(pattern);
      currentMatched = current.dir.toLowerCase().contains(pattern);
      if (candidateMatched ^ currentMatched) {
        return candidateMatched;
      }
    }

    return false;
  }

  private static class TestLocationDescriptor {

    public String dir;
    public String filePrefix;
    public String fileSuffix;
    public String ext;
    public boolean startWithLowerCase;

    public boolean isComplete() {
      return dir != null && filePrefix != null && fileSuffix != null && ext != null;
    }

    public void populate(@NotNull String testName, @NotNull VirtualFile matched) {
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
    private static final TestDataDescriptor NOTHING_FOUND = new TestDataDescriptor(Collections.<TestLocationDescriptor>emptyList());

    private final List<TestLocationDescriptor> myDescriptors = new ArrayList<TestLocationDescriptor>();

    TestDataDescriptor(Collection<TestLocationDescriptor> descriptors) {
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
    public List<String> generate(@NotNull final String testName, String root) {
      List<String> result = new ArrayList<String>();
      if (StringUtil.isEmpty(testName)) {
        return result;
      }
      for (TestLocationDescriptor descriptor : myDescriptors) {
        if (root != null && !root.equals(descriptor.dir)) continue;
        result.add(String.format(
          "%s/%s%c%s%s.%s",
          descriptor.dir, descriptor.filePrefix,
          descriptor.startWithLowerCase ? Character.toLowerCase(testName.charAt(0)) : Character.toUpperCase(testName.charAt(0)),
          testName.substring(1), descriptor.fileSuffix, descriptor.ext
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
