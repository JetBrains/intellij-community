// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.spi.SPIFileType;
import com.intellij.spi.psi.SPIClassProviderReferenceElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static com.intellij.execution.junit.JUnitExternalLibraryDescriptor.JUNIT5;
import static com.intellij.execution.junit.JUnitExternalLibraryDescriptor.JUNIT6;

public final class JUnitLauncherDependencies {
  public static final String LAUNCHER_MODULE_NAME = "org.junit.platform.launcher";

  private static final String JUPITER_ENGINE_NAME = "org.junit.jupiter.engine";
  private static final String VINTAGE_ENGINE_NAME = "org.junit.vintage.engine";
  private static final String SUITE_ENGINE_NAME = "org.junit.platform.suite.engine";

  private static final Logger LOG = Logger.getInstance(JUnitLauncherDependencies.class);

  private static final Set<String> STANDARD_JUNIT_ENGINE_CLASSES = Set.of(
    "org.junit.jupiter.engine.JupiterTestEngine",
    "org.junit.vintage.engine.VintageTestEngine",
    "org.junit.platform.launcher.core.SuiteTestEngine",
    "org.junit.platform.suite.engine.SuiteTestEngine"
  );

  public interface Downloader {
    void download(@NotNull RepositoryLibraryProperties properties) throws CantRunException;
    void putModuleOnPath(@NotNull String moduleName);
  }

  private final Project myProject;
  private final GlobalSearchScope myScope;
  private final String myRunnerName;
  private final String myLauncherVersion;
  private final boolean myModularized;

  private JUnitLauncherDependencies(@NotNull Project project,
                                    @NotNull GlobalSearchScope scope,
                                    @NotNull String runnerName,
                                    @NotNull String launcherVersion,
                                    boolean modularized) {
    myProject = project;
    myScope = scope;
    myRunnerName = runnerName;
    myLauncherVersion = launcherVersion;
    myModularized = modularized;
  }

  public static @Nullable JUnitLauncherDependencies detect(@NotNull Project project,
                                                           @NotNull GlobalSearchScope scope,
                                                           @NotNull String runnerName,
                                                           boolean modulePathAllowed) {
    String launcherVersion = getLibraryVersion("org.junit.platform.commons.JUnitException", scope, project);
    if (launcherVersion == null) {
      LOG.info("Failed to detect junit " + TestObject.RUNNER_VERSIONS.getOrDefault(runnerName, "5") +
               " launcher version, please configure explicit dependency");
      return null;
    }

    boolean modularized = modulePathAllowed &&
                          ReadAction.nonBlocking(() -> !FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, scope)
                            .isEmpty()).executeSynchronously() &&
                          VersionComparatorUtil.compare(launcherVersion, "1.5.0") >= 0;
    return new JUnitLauncherDependencies(project, scope, runnerName, launcherVersion, modularized);
  }

  public boolean isModularized() {
    return myModularized;
  }

  public void resolve(@NotNull BooleanSupplier vintageAcceptable, @NotNull Downloader downloader) throws CantRunException {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    DumbService dumbService = DumbService.getInstance(myProject);

    if (!JUnitUtil.hasPackageWithDirectories(psiFacade, LAUNCHER_MODULE_NAME, myScope)) {
      downloader.download(new RepositoryLibraryProperties("org.junit.platform", "junit-platform-launcher", myLauncherVersion));
    }

    //add standard engines only if no engine api is present
    if (hasJupiterEnginesAPI(myScope, psiFacade) && isCustomJUnit(myProject, myScope,
                                                                 JUnitStarter.JUNIT6_PARAMETER.equals(myRunnerName)
                                                                 ? JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_CANCELLATION_TOKEN
                                                                 : JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE)) {
      return;
    }

    String defaultMinVersion = JUnitStarter.JUNIT6_PARAMETER.equals(myRunnerName) ? JUNIT6.getMinVersion() : JUNIT5.getMinVersion();
    String jupiterVersion = ObjectUtils.notNull(getLibraryVersion(JUnitUtil.TEST5_ANNOTATION, myScope, myProject),
                                                defaultMinVersion == null ? "5.0.0" : defaultMinVersion);
    if (JUnitUtil.hasPackageWithDirectories(psiFacade, JUnitUtil.TEST5_PACKAGE_FQN, myScope)) {
      if (!JUnitUtil.hasPackageWithDirectories(psiFacade, JUPITER_ENGINE_NAME, myScope)) {
        downloader.download(new RepositoryLibraryProperties("org.junit.jupiter", "junit-jupiter-engine", jupiterVersion));
      }
      else if (myModularized) {
        downloader.putModuleOnPath(JUPITER_ENGINE_NAME);
      }
    }

    if (JUnitUtil.hasPackageWithDirectories(psiFacade, "org.junit.platform.suite.api", myScope)) {
      if (!JUnitUtil.hasPackageWithDirectories(psiFacade, SUITE_ENGINE_NAME, myScope)) {
        String suiteVersion = getLibraryVersion(JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_SUITE, myScope, myProject);
        if (suiteVersion != null && VersionComparatorUtil.compare(suiteVersion, "1.8.0") >= 0) {
          downloader.download(new RepositoryLibraryProperties("org.junit.platform", "junit-platform-suite-engine", suiteVersion));
        }
      }
      else if (myModularized) {
        downloader.putModuleOnPath(SUITE_ENGINE_NAME);
      }
    }

    if (!JUnitUtil.hasPackageWithDirectories(psiFacade, VINTAGE_ENGINE_NAME, myScope)) {
      if (JUnitUtil.hasPackageWithDirectories(psiFacade, "junit.framework", myScope)) {
        PsiClass junit4RunnerClass = dumbService.computeWithAlternativeResolveEnabled(
          () -> ReadAction.nonBlocking(() -> psiFacade.findClass("junit.runner.Version", myScope)).executeSynchronously());
        if (junit4RunnerClass != null && vintageAcceptable.getAsBoolean()) {
          String version = VersionComparatorUtil.compare(myLauncherVersion, "1.1.0") >= 0
                           ? jupiterVersion
                           : "4.12." + StringUtil.getShortName(myLauncherVersion);
          //don't include potentially incompatible hamcrest/junit dependency
          downloader.download(new RepositoryLibraryProperties("org.junit.vintage", "junit-vintage-engine", version, false,
                                                              ContainerUtil.emptyList()));
        }
      }
    }
    else if (myModularized) {
      downloader.putModuleOnPath(VINTAGE_ENGINE_NAME);
    }
  }

  public static boolean hasJupiterEnginesAPI(GlobalSearchScope globalSearchScope, JavaPsiFacade psiFacade) {
    return JUnitUtil.hasPackageWithDirectories(psiFacade, "org.junit.platform.engine", globalSearchScope);
  }

  public static boolean isCustomJUnit(@NotNull Project project,
                                      @NotNull GlobalSearchScope globalSearchScope,
                                      @NotNull String jupiterClassName) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    DumbService dumbService = DumbService.getInstance(project);
    return ReadAction.nonBlocking(
      () -> dumbService.isAlternativeResolveEnabled()
            ? hasCustomJupiterTestEngineUsingPsi(globalSearchScope, project, psiFacade, jupiterClassName)
            : dumbService.computeWithAlternativeResolveEnabled(
              () -> hasCustomJupiterTestEngineUsingPsi(globalSearchScope, project, psiFacade, jupiterClassName))
    ).executeSynchronously();
  }

  private static boolean hasCustomJupiterTestEngineUsingPsi(@NotNull GlobalSearchScope globalSearchScope,
                                                            @NotNull Project project,
                                                            @NotNull JavaPsiFacade psiFacade,
                                                            @NotNull String jupiterClassName) {
    PsiClass testEngine = psiFacade.findClass(jupiterClassName, globalSearchScope);
    if (testEngine == null) return false;
    Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, globalSearchScope);
    if (!files.isEmpty() && ReferencesSearch.search(testEngine, GlobalSearchScope.filesScope(project, files)).anyMatch(ref -> isCustomEngineProvided(testEngine, ref))) {
      return true;
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(globalSearchScope, SPIFileType.INSTANCE);
    return FilenameIndex.getVirtualFilesByName(jupiterClassName, scope)
      .stream()
      .map(f -> psiManager.findFile(f))
      .filter(Objects::nonNull)
      .flatMap(f -> PsiTreeUtil.findChildrenOfType(f, SPIClassProviderReferenceElement.class).stream())
      .map(r -> r.resolve())
      .filter(e -> e instanceof PsiClass)
      .map(e -> (PsiClass)e)
      .filter(c -> isCustomJupiterTestEngineName(c.getQualifiedName()))
      .anyMatch(c -> InheritanceUtil.isInheritorOrSelf(c, testEngine, true));
  }

  private static boolean isCustomEngineProvided(PsiClass testEngine, @NotNull PsiReference ref) {
    PsiProvidesStatement providesStatement = PsiTreeUtil.getParentOfType(ref.getElement(), PsiProvidesStatement.class);
    if (providesStatement != null) {
      PsiJavaCodeReferenceElement interfaceReference = providesStatement.getInterfaceReference();
      PsiReferenceList implementationList = providesStatement.getImplementationList();
      return interfaceReference != null && interfaceReference.isReferenceTo(testEngine) &&
             implementationList != null && implementationList.getReferenceElements().length > 0;
    }
    return false;
  }

  private static boolean isCustomJupiterTestEngineName(@Nullable String engineImplClassName) {
    return !STANDARD_JUNIT_ENGINE_CLASSES.contains(engineImplClassName);
  }

  private static String getLibraryVersion(String className, GlobalSearchScope globalSearchScope, Project project) {
    VirtualFile root = ReadAction.nonBlocking(() -> {
      PsiClass psiClass = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() ->
        JavaPsiFacade.getInstance(project).findClass(className, globalSearchScope)
      );
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiClass);
      if (virtualFile == null) return null;

      return ProjectFileIndex.getInstance(project).getClassRootForFile(virtualFile);
    }).executeSynchronously();

    if (root != null && StandardFileSystems.JAR_PROTOCOL.equals(root.getFileSystem().getProtocol())) {
      VirtualFile manifestFile = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
      if (manifestFile != null) {
        try (final InputStream inputStream = manifestFile.getInputStream()) {
          Attributes mainAttributes = new Manifest(inputStream).getMainAttributes();
          if ("junit.org".equals(mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR))) {
            return mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
          }
        }
        catch (IOException ignored) {
        }
      }
    }

    return null;
  }
}
