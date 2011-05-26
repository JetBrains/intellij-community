package com.intellij.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    final Pair<TestDataDescriptor, Long> cached = CACHE.get(psiClass.getQualifiedName());
    if (cached != null && cached.second + CACHE_ENTRY_TTL_MS > System.currentTimeMillis()) {
      return cached.first.isComplete() ? cached.first : null;
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
    TestDataDescriptor descriptor = new TestDataDescriptor();
    List<String> testNames = new ArrayList<String>();
    for (PsiMethod method : psiClass.getMethods()) {
      final String name = getTestName(method.getName());
      if (method == setUpMethod || method == tearDownMethod || name.equals(psiClass.getName())
          || isUtilityMethod(method, psiClass, framework))
      {
        continue;
      }
      testNames.add(name);
    }
    final Pair<String, Collection<VirtualFile>> matchedFiles = getMatchedFiles(gotoModel, testNames, psiClass);
    if (matchedFiles == null) {
      CACHE.put(psiClass.getQualifiedName(), new Pair<TestDataDescriptor, Long>(descriptor, System.currentTimeMillis()));
      return descriptor;
    }
    descriptor.populate(matchedFiles.first, matchedFiles.second);
    if (!descriptor.isComplete()) {
      CACHE.put(psiClass.getQualifiedName(), new Pair<TestDataDescriptor, Long>(descriptor, System.currentTimeMillis()));
      return null;
    }
    CACHE.put(psiClass.getQualifiedName(), new Pair<TestDataDescriptor, Long>(descriptor, System.currentTimeMillis()));
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

  @Nullable
  private static Pair<String, Collection<VirtualFile>> getMatchedFiles(@NotNull GotoFileModel gotoModel,
                                                                       @NotNull Collection<String> testNames,
                                                                       @NotNull PsiClass psiClass)
  {
    List<Trinity<NameUtil.Matcher, String, String>> input = new ArrayList<Trinity<NameUtil.Matcher, String, String>>();
    for (String testName : testNames) {
      String pattern = String.format("*%s*", testName);
      input.add(new Trinity<NameUtil.Matcher, String, String>(
        NameUtil.buildMatcher(pattern, 0, true, true, pattern.toLowerCase().equals(pattern)), testName, pattern
      ));
    }
    String dir = null;
    String testName = null;
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (String name : gotoModel.getNames(false)) {
      boolean currentNameProcessed = false;
      for (Trinity<NameUtil.Matcher, String, String> trinity : input) {
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
          if (file == null) {
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

          currentNameProcessed = true;
          final String parentPath = PathUtil.getParentPath(file.getPath());
          if (dir == null || dir.equals(parentPath)) {
            dir = parentPath;
            if (testName == null || !testName.equals(trinity.second)) {
              files.clear();
            }
            testName = trinity.second;
            files.add(file);
            continue;
          }
          if (moreRelevantPath(file, files, psiClass, trinity.second)) {
            testName = trinity.second;
            dir = parentPath;
            files.clear();
            files.add(file);
          }
        }
        if (currentNameProcessed) {
          break;
        }
      }
    }
    return (testName == null || files.isEmpty()) ? null : new Pair<String, Collection<VirtualFile>>(testName, files);
  }

  private static boolean moreRelevantPath(@NotNull VirtualFile candidate, @NotNull List<VirtualFile> current, @NotNull PsiClass psiClass,
                                          @NotNull String testName)
  {
    final String className = psiClass.getQualifiedName();
    if (className == null) {
      return false;
    }

    final String candidatePath = candidate.getPath();
    final String candidateDir = PathUtil.getParentPath(candidatePath);
    final String currentDir = PathUtil.getParentPath(current.get(0).getPath());

    // By package.
    int i = className.lastIndexOf(".");
    if (i >= 0) {
      String packageAsPath = className.substring(0, i).replace('.', '/').toLowerCase();
      if (candidateDir.toLowerCase().contains(packageAsPath) && !currentDir.toLowerCase().contains(packageAsPath)) {
        return true;
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
    if (candidateDir.toLowerCase().contains(pattern) && !currentDir.toLowerCase().contains(pattern)) {
      return true;
    }

    // By test name.
    if (PathUtil.getFileName(candidatePath).toLowerCase().startsWith(testName.toLowerCase())) {
      boolean moreRelevant = true;
      for (VirtualFile file : current) {
        if (PathUtil.getFileName(file.getPath()).toLowerCase().startsWith(testName.toLowerCase())) {
          moreRelevant = false;
          break;
        }
      }
      if (moreRelevant) {
        return true;
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

      filePrefix = fileName.substring(0, i);
      fileSuffix = fileName.substring(i + testName.length());
      ext = matched.getExtension();
      dir = matched.getParent().getPath();
    }

    @Override
    public String toString() {
      return String.format("%s/%s[...]%s.%s", dir, filePrefix, fileSuffix, ext);
    }
  }

  private static class TestDataDescriptor {

    private final List<TestLocationDescriptor> myDescriptors = new ArrayList<TestLocationDescriptor>();

    public void populate(@NotNull String testName, @NotNull Collection<VirtualFile> matched) {
      for (VirtualFile file : matched) {
        TestLocationDescriptor descriptor;
        if (myDescriptors.isEmpty()) {
          myDescriptors.add(descriptor = new TestLocationDescriptor());
        }
        else {
          final TestLocationDescriptor last = myDescriptors.get(myDescriptors.size() - 1);
          if (last.isComplete()) {
            myDescriptors.add(descriptor = new TestLocationDescriptor());
          }
          else {
            descriptor = last;
          }
        }
        descriptor.populate(testName, file);
      }
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
