// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApiStatus.Internal
public abstract class JavaCoverageClassesEnumerator {
  private static final OutputRootProcessor DIRECTORY_OUTPUT_ROOT_PROCESSOR = new DirectoryOutputRootProcessor();
  private static final OutputRootProcessor ARCHIVE_OUTPUT_ROOT_PROCESSOR = new ArchiveOutputRootProcessor();

  protected final CoverageSuitesBundle mySuite;
  protected final Project myProject;
  protected final CoverageDataManager myCoverageManager;
  private int myRootsCount;
  private int myCurrentRootsCount;

  public JavaCoverageClassesEnumerator(final @NotNull CoverageSuitesBundle suite, final @NotNull Project project) {
    mySuite = suite;
    myProject = project;
    myCoverageManager = CoverageDataManager.getInstance(myProject);
  }

  /**
   * Visit classes with the same top level name.
   */
  protected void visitClassFiles(String topLevelClassName, List<Path> files, String packageVMName) { }

  public void visitSuite() {
    Map<ModuleRequest, List<RequestRoot>> roots = AnalyseKt.collectOutputRoots(mySuite, myProject);
    myRootsCount = roots.values().stream().mapToInt(List::size).sum();
    myCurrentRootsCount = 0;
    updateProgress();

    for (var e : roots.entrySet()) {
      Module module = e.getKey().getModule();
      String packageVMName = AnalysisUtils.fqnToInternalName(e.getKey().getPackageName());
      visitSource(module, packageVMName, e.getValue());
    }
  }

  protected void visitSource(Module module, String rootPackageVMName, List<RequestRoot> roots) {
    Map<RootRequestKey, RootRequestData> requests = new LinkedHashMap<>();
    for (RequestRoot request : roots) {
      RootRequestKey key = new RootRequestKey(request.getRoot(), request.getPackagePathInRoot());
      requests.computeIfAbsent(key, _ -> new RootRequestData()).addRequest(request.getSimpleName());
    }
    for (Map.Entry<RootRequestKey, RootRequestData> entry : requests.entrySet()) {
      RootRequestKey key = entry.getKey();
      RootRequestData requestData = entry.getValue();
      try {
        visitRoot(key.root(), rootPackageVMName, requestData, key.packagePathInRoot());
      }
      finally {
        myCurrentRootsCount += requestData.getRequestsCount();
        updateProgress();
      }
    }
  }

  /**
   * @deprecated {@link #visitRoot(Path, String, RootRequestData, String)} should be used instead.
   */
  @Deprecated
  protected void visitRoot(Path packageOutputRoot,
                           String rootPackageVMName,
                           @Nullable String requestedSimpleName,
                           @NotNull String packagePathInRoot) {
    RootRequestData requestData = new RootRequestData();
    requestData.addRequest(requestedSimpleName);
    try {
      visitRoot(packageOutputRoot, rootPackageVMName, requestData, packagePathInRoot);
    }
    finally {
      myCurrentRootsCount++;
      updateProgress();
    }
  }

  private void visitRoot(Path packageOutputRoot,
                         String rootPackageVMName,
                         RootRequestData requestData,
                         @NotNull String packagePathInRoot) {
    OutputRootProcessor processor = getProcessor(packageOutputRoot);
    if (processor == null) return;
    Set<String> requestedTopLevelNames = toRequestedTopLevelNames(rootPackageVMName, requestData.getRequestedSimpleNames());
    Map<TopLevelClassKey, List<Path>> topLevelClasses = new HashMap<>();
    OutputRootContext context = new OutputRootContext(packageOutputRoot, rootPackageVMName, packagePathInRoot, requestedTopLevelNames == null);
    processor.collectClasses(context, (packageVMName, simpleName, classFile) ->
      collectTopLevelClass(topLevelClasses, packageVMName, simpleName, classFile, requestedTopLevelNames));
    visitCollectedClasses(topLevelClasses);
  }

  private static @Nullable Set<String> toRequestedTopLevelNames(String rootPackageVMName, @Nullable Set<String> requestedSimpleNames) {
    if (requestedSimpleNames == null) return null;
    Set<String> requestedTopLevelNames = new HashSet<>();
    for (String requestedSimpleName : requestedSimpleNames) {
      String requestedTopLevelName = AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(rootPackageVMName, requestedSimpleName));
      requestedTopLevelNames.add(requestedTopLevelName);
    }
    return requestedTopLevelNames;
  }

  private static @Nullable OutputRootProcessor getProcessor(@NotNull Path outputRoot) {
    if (Files.isDirectory(outputRoot)) return DIRECTORY_OUTPUT_ROOT_PROCESSOR;
    if (Files.isRegularFile(outputRoot)) return ARCHIVE_OUTPUT_ROOT_PROCESSOR;
    return null;
  }

  /**
   * Collects class files generated from the requested top-level classes in the given package.
   * Supports both directory output roots and archive output roots, such as jars. Returned paths for archive entries use
   * the {@code /path/to/archive.jar!/entry/name.class} form.
   * <p>
   * The {@code topLevelClassNames} set contains simple source-level class names. A class file is included when its simple
   * name is equal to one of these names or starts with {@code <top-level-name>$}, which covers nested, inner, local, and
   * anonymous classes generated for that top-level class.
   */
  public static @NotNull List<Path> collectClassFiles(@NotNull Path outputRoot,
                                                      @NotNull String packageVMName,
                                                      @NotNull Set<String> topLevelClassNames) {
    if (topLevelClassNames.isEmpty()) return List.of();
    OutputRootProcessor processor = getProcessor(outputRoot);
    if (processor == null) return List.of();

    List<Path> classFiles = new ArrayList<>();
    OutputRootContext context = new OutputRootContext(outputRoot, packageVMName, packageVMName, false);
    processor.collectClasses(context, (classPackageVMName, simpleName, classFile) -> {
      if (!classPackageVMName.equals(packageVMName)) return;
      if (isGeneratedFromTopLevelClass(simpleName, topLevelClassNames)) {
        classFiles.add(classFile);
      }
    });
    return classFiles;
  }

  private static boolean isGeneratedFromTopLevelClass(@NotNull String simpleName, @NotNull Set<String> topLevelClassNames) {
    for (String topLevelClassName : topLevelClassNames) {
      if (simpleName.equals(topLevelClassName) ||
          simpleName.startsWith(topLevelClassName) && simpleName.charAt(topLevelClassName.length()) == '$') {
        return true;
      }
    }
    return false;
  }

  private void visitCollectedClasses(Map<TopLevelClassKey, List<Path>> topLevelClasses) {
    for (Map.Entry<TopLevelClassKey, List<Path>> entry : topLevelClasses.entrySet()) {
      TopLevelClassKey key = entry.getKey();
      visitClassFiles(key.topLevelClassName, entry.getValue(), key.packageVMName);
    }
  }

  private static void collectTopLevelClass(Map<TopLevelClassKey, List<Path>> topLevelClasses,
                                           String packageVMName,
                                           String simpleName,
                                           Path classFile,
                                           @Nullable Set<String> requestedTopLevelNames) {
    String classFqVMName = AnalysisUtils.buildVMName(packageVMName, simpleName);
    String topLevelClassSrcFQName = AnalysisUtils.getSourceToplevelFQName(classFqVMName);
    if (requestedTopLevelNames != null && !requestedTopLevelNames.contains(topLevelClassSrcFQName)) return;
    TopLevelClassKey key = new TopLevelClassKey(topLevelClassSrcFQName, packageVMName);
    topLevelClasses.computeIfAbsent(key, _ -> new ArrayList<>()).add(classFile);
  }

  private void updateProgress() {
    if (myRootsCount <= 1) return;
    final ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator == null) return;
    progressIndicator.setIndeterminate(false);
    progressIndicator.setFraction(myCurrentRootsCount / (double)myRootsCount);
  }

  private record PackageData(String packageVMName, List<Path> children) {
  }

  private record RootRequestKey(Path root, String packagePathInRoot) {
  }

  private record TopLevelClassKey(String topLevelClassName, String packageVMName) {
  }

  private record OutputRootContext(Path outputRoot,
                                   String rootPackageVMName,
                                   @NotNull String packagePathInRoot,
                                   boolean includeSubpackages) {
  }

  private interface OutputRootProcessor {
    void collectClasses(@NotNull OutputRootContext context, @NotNull ClassFileConsumer collector);
  }

  private interface ClassFileConsumer {
    void accept(String packageVMName, String simpleName, Path classFile);
  }

  private static final class DirectoryOutputRootProcessor implements OutputRootProcessor {
    @Override
    public void collectClasses(@NotNull OutputRootContext context, @NotNull ClassFileConsumer collector) {
      Path packageRoot = PackageAnnotator.findRelativePath(context.packagePathInRoot(), context.outputRoot());
      if (!Files.exists(packageRoot)) return;
      Stack<PackageData> stack = new Stack<>(new PackageData(context.rootPackageVMName(), listChildren(packageRoot)));
      while (!stack.isEmpty()) {
        ProgressIndicatorProvider.checkCanceled();
        PackageData packageData = stack.pop();
        String packageVMName = packageData.packageVMName;
        for (Path child : packageData.children) {
          if (AnalysisUtils.isClassFile(child)) {
            collector.accept(packageVMName, AnalysisUtils.getClassName(child), child);
          }
          else if (context.includeSubpackages() && Files.isDirectory(child)) {
            String childPackageVMName = AnalysisUtils.buildVMName(packageVMName, child.getFileName().toString());
            stack.push(new PackageData(childPackageVMName, listChildren(child)));
          }
        }
      }
    }

    private static @NotNull List<Path> listChildren(@NotNull Path packageRoot) {
      try (var children = Files.list(packageRoot)) {
        return children.toList();
      }
      catch (IOException ignored) {
        return List.of();
      }
    }
  }

  private static final class ArchiveOutputRootProcessor implements OutputRootProcessor {
    @Override
    public void collectClasses(@NotNull OutputRootContext context, @NotNull ClassFileConsumer collector) {
      String packagePathInRoot = context.packagePathInRoot();
      String prefix = packagePathInRoot.isEmpty() ? "" : packagePathInRoot + "/";
      try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(context.outputRoot()))) {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
          ProgressIndicatorProvider.checkCanceled();
          if (entry.isDirectory()) continue;
          String entryName = entry.getName();
          if (!entryName.endsWith(".class")) continue;
          if (!prefix.isEmpty() && !entryName.startsWith(prefix)) continue;

          String relativePath = prefix.isEmpty() ? entryName : entryName.substring(prefix.length());
          int slashIndex = relativePath.lastIndexOf('/');
          if (!context.includeSubpackages() && slashIndex >= 0) continue;

          String packageVMName = slashIndex < 0 ? context.rootPackageVMName() :
                                 AnalysisUtils.buildVMName(context.rootPackageVMName(), relativePath.substring(0, slashIndex));
          int simpleNameStart = slashIndex + 1;
          String simpleName = relativePath.substring(simpleNameStart, relativePath.length() - ".class".length());
          Path classFile = AnalysisUtils.toArchiveEntryPath(context.outputRoot(), entryName);
          collector.accept(packageVMName, simpleName, classFile);
        }
      }
      catch (IOException ignored) {
      }
    }
  }

  private static final class RootRequestData {
    private final Set<String> myRequestedSimpleNames = new HashSet<>();
    private boolean myIncludeAllClasses;
    private int myRequestsCount;

    private void addRequest(@Nullable String requestedSimpleName) {
      myRequestsCount++;
      if (requestedSimpleName == null) {
        myIncludeAllClasses = true;
        myRequestedSimpleNames.clear();
      }
      else if (!myIncludeAllClasses) {
        myRequestedSimpleNames.add(requestedSimpleName);
      }
    }

    private @Nullable Set<String> getRequestedSimpleNames() {
      return myIncludeAllClasses ? null : myRequestedSimpleNames;
    }

    private int getRequestsCount() {
      return myRequestsCount;
    }
  }

  /**
   * Collect output roots for the specified module.
   *
   * @param includeTests if true, returns both production and test output roots; if false, returns only production roots
   */
  public static VirtualFile @NotNull [] getRoots(final CoverageDataManager manager, final Module module, final boolean includeTests) {
    final VirtualFile[] files = manager.doInReadActionIfProjectOpen(() -> {
      OrderEnumerator enumerator = OrderEnumerator.orderEntries(module)
        .withoutSdk()
        .withoutLibraries()
        .withoutDepModules();
      if (!includeTests) {
        enumerator = enumerator.productionOnly();
      }
      return enumerator.classes().getRoots();
    });
    if (files == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    return files;
  }
}
