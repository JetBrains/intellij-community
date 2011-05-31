package com.intellij.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * There is a possible case that particular test class is not properly configured with test annotations but uses test data files.
 * This class contains utility methods for guessing test data files location and name patterns from existing one.
 *
 * @author Denis Zhdanov
 * @since 5/24/11 2:28 PM
 */
public class TestDataGuessByExistingFilesUtil {

  private static final long CACHE_ENTRY_TTL_MS = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

  private static final Map<String, Pair<TestDataDescriptor, Long>> CACHE = new ConcurrentHashMap<String, Pair<TestDataDescriptor, Long>>();

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
    GotoFileModel model = new GotoFileModel(psiFile.getProject());
    TestDataDescriptor descriptor = buildDescriptorFromExistingTestData(psiFile, model);
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
    return methodName.startsWith("test") ? methodName.substring("test".length()) : methodName;
  }

  @Nullable
  private static TestDataDescriptor buildDescriptorFromExistingTestData(@NotNull PsiFile file, @NotNull GotoFileModel gotoModel) {
    final PsiClass psiClass = PsiTreeUtil.getChildOfType(file, PsiClass.class);
    if (psiClass == null) {
      return null;
    }

    final String qualifiedName = psiClass.getQualifiedName();
    final Pair<TestDataDescriptor, Long> cached = CACHE.get(qualifiedName);
    if (cached != null) {
      if (cached.first.isComplete()) {
        return cached.first;
      }
      if (cached.second > System.currentTimeMillis()) {
        return null;
      }
    }

    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    TestFramework framework = null;
    for (TestFramework each : frameworks) {
      if (each.isTestClass(psiClass)) {
        framework = each;
        break;
      }
    }
    if (framework == null) {
      return null;
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
    final TestDataDescriptor descriptor = buildDescriptor(gotoModel, fileIndex, testNames, psiClass);
    CACHE.put(qualifiedName, new Pair<TestDataDescriptor, Long>(descriptor, System.currentTimeMillis() + CACHE_ENTRY_TTL_MS));
    return descriptor;
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

  @NotNull
  private static TestDataDescriptor buildDescriptor(@NotNull GotoFileModel gotoModel,
                                                    @NotNull ProjectFileIndex fileIndex,
                                                    @NotNull Collection<String> testNames,
                                                    @NotNull PsiClass psiClass)
  {
    List<Trinity<Matcher, String, String>> input = new ArrayList<Trinity<Matcher, String, String>>();
    Set<String> testNamesLowerCase = new HashSet<String>();
    for (String testName : testNames) {
      String pattern = String.format("*%s*", testName);
      input.add(new Trinity<Matcher, String, String>(
        NameUtil.buildMatcher(pattern, 0, true, true, pattern.toLowerCase().equals(pattern)), testName, pattern
      ));
      testNamesLowerCase.add(testName.toLowerCase());
    }
    Set<TestLocationDescriptor> descriptors = new HashSet<TestLocationDescriptor>();
    for (String name : gotoModel.getNames(false)) {
      ProgressManager.checkCanceled();
      boolean currentNameProcessed = false;
      for (Trinity<Matcher, String, String> trinity : input) {
        if (!trinity.first.matches(name)) {
          continue;
        }

        final Object[] elements = gotoModel.getElementsByName(name, false, trinity.third);
        if (elements == null) {
          continue;
        }
        for (Object element : elements) {
          if (!(element instanceof PsiFile)) {
            continue;
          }
          final VirtualFile file = ((PsiFile)element).getVirtualFile();
          if (file == null || fileIndex.isInSource(file)) {
            continue;
          }


          final String filePath = PathUtil.getFileName(file.getPath()).toLowerCase();
          int i = filePath.indexOf(trinity.second.toLowerCase());
          // Skip files that doesn't contain target test name and files that contain digit after target test name fragment.
          // Example: there are tests with names 'testEnter()' and 'testEnter2()' and we don't want test data file 'testEnter2'
          // to be matched to the test 'testEnter()'.
          if (i < 0 || (i + trinity.second.length() < filePath.length())
                       && Character.isDigit(filePath.charAt(i + trinity.second.length())))
          {
            continue;
          }

          TestLocationDescriptor current = new TestLocationDescriptor();
          current.populate(trinity.second, file);
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
            if (testName.equals(trinity.second)) {
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
    String pattern = className.toLowerCase();
    if (pattern.endsWith("test")) {
      pattern = pattern.substring(0, pattern.length() - "Test".length());
    }
    i = pattern.lastIndexOf('.');
    if (i >= 0) {
      pattern = pattern.substring(i + 1);
    }
    candidateMatched = candidate.dir.toLowerCase().contains(pattern);
    currentMatched = current.dir.toLowerCase().contains(pattern);
    if (candidateMatched ^ currentMatched) {
      return candidateMatched;
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
      ext = matched.getExtension();
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
      List<String> result = new ArrayList<String>();
      if (StringUtil.isEmpty(testName)) {
        return result;
      }
      for (TestLocationDescriptor descriptor : myDescriptors) {
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
