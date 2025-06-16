// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.roots.impl.indexing.DirectorySpec;
import com.intellij.openapi.roots.impl.indexing.ProjectStructureDslKt;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.rules.ProjectModelRule;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ThreeState;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.builders.*;
import kotlin.Pair;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.junit.*;
import org.junit.rules.ExternalResource;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.testFramework.UsefulTestCase.*;
import static com.intellij.util.ThreeState.UNSURE;
import static com.intellij.util.ThreeState.YES;

@RunsInEdt
@SuppressWarnings("KotlinInternalInJava")
public abstract class DependenciesIndexedStatusServiceBaseTest {
  @ClassRule
  public static final ApplicationRule appRule = new ApplicationRule();

  @Rule
  public final ProjectModelRule projectModelRule = new ProjectModelRule();
  @Rule
  public final EdtRule edtRule = new EdtRule();
  @Rule
  public final DisposableRule disposableRule = new DisposableRule();
  @Rule
  public final TempDirectory tempDirectory = new TempDirectory();

  @Rule
  public final ExternalResource dependenciesIndexedStatusServiceEnabler = new ExternalResource() {
    @Override
    protected void before() {
      TestModeFlags.set(DependenciesIndexedStatusService.ENFORCEMENT_USAGE_TEST_MODE_FLAG, true, disposableRule.getDisposable());
    }
  };

  protected void setUp(List<AdditionalLibraryRootsProvider> providers,
                       List<IndexableSetContributor> contributors,
                       List<DirectoryIndexExcludePolicy> policies) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ((ExtensionPointImpl<@NotNull AdditionalLibraryRootsProvider>)AdditionalLibraryRootsProvider.EP_NAME.getPoint()).
        maskAll(providers, disposableRule.getDisposable(), true);

      ((ExtensionPointImpl<@NotNull IndexableSetContributor>)IndexableSetContributor.EP_NAME.getPoint()).
        maskAll(contributors, disposableRule.getDisposable(), true);

      ((ExtensionPointImpl<@NotNull DirectoryIndexExcludePolicy>)DirectoryIndexExcludePolicy.EP_NAME.getPoint(getProject())).
        maskAll(policies, disposableRule.getDisposable(), true);

      fireRootsChangedTotalRescan(RootsChangeRescanningInfo.TOTAL_RESCAN);
    });
  }

  public static class AdditionalLibraryProviderTest extends DependenciesIndexedStatusServiceBaseTest {
    private final List<VirtualFile> libraryRootsNoId = new ArrayList<>();
    private final List<VirtualFile> libraryRootsId1 = new ArrayList<>();
    private final List<VirtualFile> libraryRootsId2 = new ArrayList<>();

    @Before
    public void setUp() {
      AdditionalLibraryRootsProvider provider = new AdditionalLibraryRootsProvider() {
        @Override
        public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
          ArrayList<SyntheticLibrary> result = new ArrayList<>();
          addLibrary(result, libraryRootsNoId, null);
          addLibrary(result, libraryRootsId1, "ID1");
          addLibrary(result, libraryRootsId2, "ID2");
          return result;
        }
      };
      setUp(Collections.singletonList(provider), Collections.emptyList(), Collections.emptyList());
    }

    private static void addLibrary(@NotNull List<SyntheticLibrary> list, @NotNull List<VirtualFile> contentRoots, @Nullable String id) {
      if (contentRoots.isEmpty()) return;
      if (id == null) {
        list.add(SyntheticLibrary.newImmutableLibrary(contentRoots));
      }
      else {
        list.add(SyntheticLibrary.newImmutableLibrary(id, contentRoots, Collections.emptyList(), Collections.emptySet(), null));
      }
    }

    @After
    public void tearDown() {
      libraryRootsNoId.clear();
      libraryRootsId1.clear();
      libraryRootsId2.clear();
    }

    @Test
    public void checkAddingSyntheticLibraryRootInSingleLibraryWithoutId() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      libraryRootsNoId.add(first);
      assertRescanningLibraries(YES, first);

      assertNothingToRescan();

      libraryRootsNoId.add(second);
      assertRescanningLibraries(YES, second);

      assertNothingToRescan();

      libraryRootsNoId.remove(second);
      assertNothingToRescan();
    }

    @Test
    public void checkAddingSyntheticLibraryRootInLibraryWithId() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");
      {
        VirtualFile constantAdditionalLib = tempDirectory.newVirtualDirectory("constantAdditionalLib/");
        libraryRootsId1.add(constantAdditionalLib);
        assertRescanningLibraries(YES, constantAdditionalLib);
      }

      assertNothingToRescan();
      libraryRootsId2.add(first);
      assertRescanningLibraries(YES, first);
      assertNothingToRescan();

      libraryRootsId2.add(second);
      assertRescanningLibraries(YES, second);
      assertNothingToRescan();
    }

    @Test
    public void checkAddingSyntheticLibraryRootInNonSingleLibraryWithoutId() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");
      {
        VirtualFile constantAdditionalLib = tempDirectory.newVirtualDirectory("constantAdditionalLib/");
        libraryRootsId1.add(constantAdditionalLib);
        assertRescanningLibraries(YES, constantAdditionalLib);
      }

      assertNothingToRescan();
      libraryRootsNoId.add(first);
      assertRescanningLibraries(YES, first);

      assertRescanningLibraries(UNSURE, first);

      libraryRootsNoId.add(second);
      assertRescanningLibraries(YES, first, second);

      assertRescanningLibraries(UNSURE, first, second);

      libraryRootsNoId.remove(first);
      assertRescanningLibraries(YES, second);
      assertRescanningLibraries(UNSURE, second);
    }

    @Test
    public void checkAddingSyntheticLibraryRootWithCancelledIndexingInSingleLibraryWithoutId() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      libraryRootsNoId.add(first);
      assertRescanningLibraries(ThreeState.NO, first);

      assertRescanningLibraries(UNSURE, first);

      libraryRootsNoId.add(second);
      assertRescanningLibraries(UNSURE, first, second);
      assertRescanningLibraries(ThreeState.NO, second);
      assertRescanningLibraries(UNSURE, first, second);

      libraryRootsNoId.clear();
      assertNothingToRescan();
    }

    @Test
    public void checkSwitchingRootBetweenLibrariesWithIds() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      libraryRootsId1.add(first);
      libraryRootsId2.add(second);
      assertRescanningLibraries(YES, first, second);

      assertNothingToRescan();
      libraryRootsId1.add(second);
      assertRescanningLibraries(YES, second);

      assertNothingToRescan();
      libraryRootsId2.add(first);
      assertRescanningLibraries(YES, first);

      libraryRootsId1.clear();
      libraryRootsId2.clear();
      assertNothingToRescanAndFinishIndexing();

      libraryRootsId1.add(first);
      libraryRootsId2.add(second);
      assertRescanningLibraries(YES, first, second);

      assertNothingToRescan();
      libraryRootsId1.add(second);
      libraryRootsId2.add(first);
      assertRescanningLibraries(YES, first, second);
    }

    @Test
    public void checkReorderingOfRoots() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      libraryRootsId1.add(first);
      libraryRootsId1.add(second);
      assertRescanningLibraries(YES, first, second);
      assertNothingToRescan();

      libraryRootsId1.clear();
      libraryRootsId1.add(second);
      libraryRootsId1.add(first);
      assertNothingToRescan();
    }


    private void assertRescanningLibraries(ThreeState finishIndexingWithStatus, VirtualFile... roots) {
      DependenciesIndexedStatusService statusService = DependenciesIndexedStatusService.getInstance(getProject());
      @Nullable Pair<@NotNull Collection<? extends IndexableIteratorBuilder>, @NotNull StatusMark> statusPair;
      statusPair = statusService.getDeltaWithLastIndexedStatus();
      Collection<? extends IndexableIteratorBuilder> builders = statusPair.getFirst();
      List<VirtualFile> actualRoots = new ArrayList<>();
      for (IndexableIteratorBuilder builder : builders) {
        assertInstanceOf(builder, SyntheticLibraryIteratorBuilder.class);
        actualRoots.addAll(((SyntheticLibraryIteratorBuilder)builder).getRoots());
      }
      assertContainsElements(actualRoots, roots);

      finishIndexing(finishIndexingWithStatus, statusPair.getSecond(), statusService);
    }
  }

  public static class IndexableSetContributorTest extends DependenciesIndexedStatusServiceBaseTest {
    private final List<VirtualFile> indexableProjectRoots = new ArrayList<>();
    private final List<VirtualFile> indexableRoots = new ArrayList<>();

    @Before
    public void setUp() {
      IndexableSetContributor contributor = new IndexableSetContributor() {
        @Override
        public @NotNull Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
          return Set.copyOf(indexableProjectRoots);
        }

        @Override
        public @NotNull Set<VirtualFile> getAdditionalRootsToIndex() {
          return Set.copyOf(indexableRoots);
        }
      };
      setUp(Collections.emptyList(), Collections.singletonList(contributor), Collections.emptyList());
    }

    @After
    public void tearDown() {
      indexableProjectRoots.clear();
      indexableRoots.clear();
    }

    @Test
    public void checkAddingAppRoots() {
      doCheckAddingRoots(indexableRoots);
    }

    @Test
    public void checkAddingProjectRoots() {
      doCheckAddingRoots(indexableProjectRoots);
    }

    private void doCheckAddingRoots(List<VirtualFile> roots) {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      roots.add(first);
      assertRescanningIndexableSets(YES, first);

      assertNothingToRescan();

      roots.add(second);
      assertRescanningIndexableSets(YES, second);

      assertNothingToRescan();
      roots.remove(second);
      assertNothingToRescan();
    }

    @Test
    public void checkSwitchingRootBetweenProjectAndApplicationRoots() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      indexableProjectRoots.add(first);
      indexableRoots.add(second);
      assertRescanningIndexableSets(YES, first, second);

      assertNothingToRescan();
      indexableProjectRoots.add(second);
      assertRescanningIndexableSets(YES, second);

      assertNothingToRescan();
      indexableRoots.add(first);
      assertRescanningIndexableSets(YES, first);

      indexableProjectRoots.clear();
      indexableRoots.clear();
      assertNothingToRescanAndFinishIndexing();

      indexableProjectRoots.add(first);
      indexableRoots.add(second);
      assertRescanningIndexableSets(YES, first, second);

      assertNothingToRescan();
      indexableProjectRoots.add(second);
      indexableRoots.add(first);
      assertRescanningIndexableSets(YES, first, second);
    }

    @Test
    public void checkAddingProjectRootsWithCancelledIndexing() {
      doCheckAddingRootsWithCancelledIndexing(indexableProjectRoots);
    }

    @Test
    public void checkAddingApplicationRootsWithCancelledIndexing() {
      doCheckAddingRootsWithCancelledIndexing(indexableRoots);
    }

    private void doCheckAddingRootsWithCancelledIndexing(List<VirtualFile> roots) {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      roots.add(first);
      assertRescanningIndexableSets(ThreeState.NO, first);

      assertRescanningIndexableSets(UNSURE, first);

      roots.add(second);
      assertRescanningIndexableSets(UNSURE, first, second);
      assertRescanningIndexableSets(ThreeState.NO, second);
      assertRescanningIndexableSets(UNSURE, first, second);

      roots.clear();
      assertNothingToRescan();
    }

    @Test
    public void checkReorderingOfRoots() {
      VirtualFile first = tempDirectory.newVirtualDirectory("first/");
      VirtualFile second = tempDirectory.newVirtualDirectory("second/");

      assertNothingToRescan();
      indexableRoots.add(first);
      indexableRoots.add(second);
      assertRescanningIndexableSets(YES, first, second);
      assertNothingToRescan();

      indexableRoots.clear();
      indexableRoots.add(second);
      indexableRoots.add(first);
      assertNothingToRescan();

      assertNothingToRescan();
      indexableProjectRoots.add(first);
      indexableProjectRoots.add(second);
      assertRescanningIndexableSets(YES, first, second);
      assertNothingToRescan();

      indexableProjectRoots.clear();
      indexableProjectRoots.add(second);
      indexableProjectRoots.add(first);
      assertNothingToRescan();
    }

    private void assertRescanningIndexableSets(ThreeState finishIndexingWithStatus, VirtualFile... roots) {
      DependenciesIndexedStatusService statusService = DependenciesIndexedStatusService.getInstance(getProject());
      @Nullable Pair<@NotNull Collection<? extends IndexableIteratorBuilder>, @NotNull StatusMark> statusPair;
      statusPair = statusService.getDeltaWithLastIndexedStatus();
      Collection<? extends IndexableIteratorBuilder> builders = statusPair.getFirst();
      List<VirtualFile> actualRoots = new ArrayList<>();
      for (IndexableIteratorBuilder builder : builders) {
        assertInstanceOf(builder, IndexableSetContributorFilesIteratorBuilder.class);
        actualRoots.addAll(((IndexableSetContributorFilesIteratorBuilder)builder).getProvidedRootsToIndex());
      }
      assertContainsElements(actualRoots, roots);

      finishIndexing(finishIndexingWithStatus, statusPair.getSecond(), statusService);
    }
  }

  public static class DirectoryIndexExcludePolicyTest extends DependenciesIndexedStatusServiceBaseTest {
    private final List<String> excludedUrls = new ArrayList<>();
    private final List<VirtualFile> excludedFromSdk = new ArrayList<>();

    @Before
    public void setUp() {
      DirectoryIndexExcludePolicy policy = new DirectoryIndexExcludePolicy() {
        @Override
        public String @NotNull [] getExcludeUrlsForProject() {
          return ArrayUtil.toStringArray(excludedUrls);
        }

        @Override
        public Function<Sdk, List<VirtualFile>> getExcludeSdkRootsStrategy() {
          return sdk -> excludedFromSdk;
        }
      };
      setUp(Collections.emptyList(), Collections.emptyList(), Collections.singletonList(policy));
    }

    @After
    public void tearDown() {
      excludedUrls.clear();
      excludedFromSdk.clear();
    }

    @Test
    public void checkExcludingProjectPart() {
      Ref<DirectorySpec> firstDirSpec = new Ref<>();
      Ref<DirectorySpec> secondDirSpec = new Ref<>();
      ProjectStructureDslKt.createJavaModule(projectModelRule, "module", moduleContentBuilder -> {
        moduleContentBuilder.source("sourceRoot", JavaSourceRootType.SOURCE, contentBuilder -> {
          firstDirSpec.set(contentBuilder.dir("first", builder1 -> Unit.INSTANCE));
          secondDirSpec.set(contentBuilder.dir("second", builder1 -> Unit.INSTANCE));
          return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
      });
      VirtualFile firstDir = ProjectStructureDslKt.resolveVirtualFile(firstDirSpec.get());
      VirtualFile secondDir = ProjectStructureDslKt.resolveVirtualFile(secondDirSpec.get());

      assertNothingToRescan();
      updateExcludedRoots(urls -> {
        urls.add(firstDir.getUrl());
        urls.add(secondDir.getUrl());
      });
      assertNothingToRescanAndFinishIndexing();

      updateExcludedRoots(urls -> urls.clear());
      assertRescanningProjectContent(UNSURE, firstDir, secondDir);

      updateExcludedRoots(urls -> {
        urls.add(secondDir.getUrl());
      });
      assertRescanningProjectContent(YES, firstDir);

      updateExcludedRoots(urls -> urls.clear());
      assertRescanningProjectContent(YES, secondDir);
    }

    @Test
    public void checkReorderingExcludingProjectPart() {
      Ref<DirectorySpec> firstDirSpec = new Ref<>();
      Ref<DirectorySpec> secondDirSpec = new Ref<>();
      ProjectStructureDslKt.createJavaModule(projectModelRule, "module", moduleContentBuilder -> {
        moduleContentBuilder.source("sourceRoot", JavaSourceRootType.SOURCE, contentBuilder -> {
          firstDirSpec.set(contentBuilder.dir("first", builder1 -> Unit.INSTANCE));
          secondDirSpec.set(contentBuilder.dir("second", builder1 -> Unit.INSTANCE));
          return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
      });
      VirtualFile firstDir = ProjectStructureDslKt.resolveVirtualFile(firstDirSpec.get());
      VirtualFile secondDir = ProjectStructureDslKt.resolveVirtualFile(secondDirSpec.get());

      assertNothingToRescan();
      updateExcludedRoots(urls -> {
        urls.add(firstDir.getUrl());
        urls.add(secondDir.getUrl());
      });
      assertNothingToRescanAndFinishIndexing();

      updateExcludedRoots(urls -> {
        urls.clear();
        urls.add(secondDir.getUrl());
        urls.add(firstDir.getUrl());
      });
      assertNothingToRescan();
    }

    @Test
    public void checkExcludingSdkPart() {
      Ref<DirectorySpec> classesDirSpec = new Ref<>();
      Ref<DirectorySpec> sourcesDirSpec = new Ref<>();

      VirtualFile sdkRoot = tempDirectory.newVirtualDirectory("sdkRoot");
      ProjectStructureDslKt.buildDirectoryContent(sdkRoot, contentBuilder -> {
        contentBuilder.dir("sdk", sdkBuilder -> {
          classesDirSpec.set(sdkBuilder.dir("classes", builder -> Unit.INSTANCE));
          sourcesDirSpec.set(sdkBuilder.dir("sources", builder -> Unit.INSTANCE));
          return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
      });

      VirtualFile classesDir = ProjectStructureDslKt.resolveVirtualFile(classesDirSpec.get());
      VirtualFile sourcesDir = ProjectStructureDslKt.resolveVirtualFile(sourcesDirSpec.get());

      Sdk sdk = projectModelRule.addSdk("sdkName", sdkModificator -> {
        sdkModificator.addRoot(classesDir, OrderRootType.CLASSES);
        sdkModificator.addRoot(sourcesDir, OrderRootType.SOURCES);
        return Unit.INSTANCE;
      });

      Module module = projectModelRule.createModule("module");
      ModuleRootModificationUtil.setModuleSdk(module, sdk);

      assertNothingToRescan();
      updateExcludedRoots(urls -> {
        urls.add(classesDir.getUrl());
        urls.add(sourcesDir.getUrl());
      });
      assertNothingToRescanAndFinishIndexing();

      updateExcludedRoots(urls -> urls.clear());
      assertRescanningSdkContent(UNSURE, classesDir, sourcesDir);

      updateExcludedRoots(urls -> {
        urls.add(sourcesDir.getUrl());
      });
      assertRescanningSdkContent(YES, classesDir);

      updateExcludedRoots(urls -> urls.clear());
      assertRescanningSdkContent(YES, sourcesDir);
    }

    @Test
    public void checkExcludingSdkPartWithSdkStrategy() {
      Ref<DirectorySpec> classesDirSpec = new Ref<>();
      Ref<DirectorySpec> classesExcludedDirSpec = new Ref<>();
      Ref<DirectorySpec> sourcesDirSpec = new Ref<>();
      Ref<DirectorySpec> sourcesExcludedDirSpec = new Ref<>();

      VirtualFile sdkRoot = tempDirectory.newVirtualDirectory("sdkRoot");
      ProjectStructureDslKt.buildDirectoryContent(sdkRoot, contentBuilder -> {
        contentBuilder.dir("sdk", sdkBuilder -> {
          classesDirSpec.set(sdkBuilder.dir("classes", builder -> {
            classesExcludedDirSpec.set(builder.dir("excludedInClasses", ignored -> Unit.INSTANCE));
            return Unit.INSTANCE;
          }));
          sourcesDirSpec.set(sdkBuilder.dir("sources", builder -> {
            sourcesExcludedDirSpec.set(builder.dir("excludedInSources", ignored -> Unit.INSTANCE));
            return Unit.INSTANCE;
          }));
          return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
      });

      VirtualFile classesDir = ProjectStructureDslKt.resolveVirtualFile(classesDirSpec.get());
      VirtualFile classesExcludedDir = ProjectStructureDslKt.resolveVirtualFile(classesExcludedDirSpec.get());
      VirtualFile sourcesDir = ProjectStructureDslKt.resolveVirtualFile(sourcesDirSpec.get());
      VirtualFile sourcesExcludedDir = ProjectStructureDslKt.resolveVirtualFile(sourcesExcludedDirSpec.get());

      Sdk sdk = projectModelRule.addSdk("sdkName", sdkModificator -> {
        sdkModificator.addRoot(classesDir, OrderRootType.CLASSES);
        sdkModificator.addRoot(sourcesDir, OrderRootType.SOURCES);
        return Unit.INSTANCE;
      });

      Module module = projectModelRule.createModule("module");
      ModuleRootModificationUtil.setModuleSdk(module, sdk);

      assertNothingToRescan();
      updateExcludedFromSdkRoots(roots -> {
        roots.add(classesExcludedDir);
        roots.add(sourcesExcludedDir);
      });
      assertNothingToRescanAndFinishIndexing();

      updateExcludedFromSdkRoots(urls -> urls.clear());
      assertRescanningSdkContent(UNSURE, classesExcludedDir, sourcesExcludedDir);

      updateExcludedFromSdkRoots(roots -> {
        roots.add(sourcesExcludedDir);
      });
      assertRescanningSdkContent(YES, classesExcludedDir);

      updateExcludedFromSdkRoots(roots -> roots.clear());
      assertRescanningSdkContent(YES, sourcesExcludedDir);
    }

    @Test
    public void checkExcludingModuleLibraryPart() {
      doTestLibraryExcluding(libRootPair -> {
        Module module = ProjectStructureDslKt.createJavaModule(projectModelRule, "module", moduleContentBuilder -> {
          return Unit.INSTANCE;
        });

        ModuleRootModificationUtil.addModuleLibrary(module, "libName",
                                                    Collections.singletonList(libRootPair.getFirst().getUrl()),
                                                    Collections.singletonList(libRootPair.getSecond().getUrl()));
      });
    }

    @Test
    public void checkExcludingProjectLibraryPart() {
      doTestLibraryExcluding(libRootPair -> {
        Module module = ProjectStructureDslKt.createJavaModule(projectModelRule, "module", moduleContentBuilder -> {
          return Unit.INSTANCE;
        });

        LibraryEx library = projectModelRule.addProjectLevelLibrary("libName", setup -> {
          setup.addRoot(libRootPair.getFirst(), OrderRootType.CLASSES);
          setup.addRoot(libRootPair.getSecond(), OrderRootType.SOURCES);
          return Unit.INSTANCE;
        });
        ModuleRootModificationUtil.addDependency(module, library);
      });
    }

    @Test
    public void checkExcludingGlobalLibraryPart() {
      doTestLibraryExcluding(libRootPair -> {
        Module module = ProjectStructureDslKt.createJavaModule(projectModelRule, "module", moduleContentBuilder -> {
          return Unit.INSTANCE;
        });

        LibraryEx library = projectModelRule.addApplicationLevelLibrary("libName", setup -> {
          setup.addRoot(libRootPair.getFirst(), OrderRootType.CLASSES);
          setup.addRoot(libRootPair.getSecond(), OrderRootType.SOURCES);
          return Unit.INSTANCE;
        });
        ModuleRootModificationUtil.addDependency(module, library);
      });
    }

    private void doTestLibraryExcluding(Consumer<Pair<VirtualFile, VirtualFile>> libraryCreator) {
      Ref<DirectorySpec> classesDirSpec = new Ref<>();
      Ref<DirectorySpec> sourcesDirSpec = new Ref<>();

      VirtualFile sdkRoot = tempDirectory.newVirtualDirectory("sdkRoot");
      ProjectStructureDslKt.buildDirectoryContent(sdkRoot, contentBuilder -> {
        contentBuilder.dir("library", sdkBuilder -> {
          classesDirSpec.set(sdkBuilder.dir("classes", builder -> Unit.INSTANCE));
          sourcesDirSpec.set(sdkBuilder.dir("sources", builder -> Unit.INSTANCE));
          return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
      });

      VirtualFile classesDir = ProjectStructureDslKt.resolveVirtualFile(classesDirSpec.get());
      VirtualFile sourcesDir = ProjectStructureDslKt.resolveVirtualFile(sourcesDirSpec.get());

      libraryCreator.accept(new Pair<>(classesDir, sourcesDir));

      assertNothingToRescan();
      updateExcludedRoots(urls -> {
        urls.add(classesDir.getUrl());
        urls.add(sourcesDir.getUrl());
      });
      assertNothingToRescanAndFinishIndexing();

      updateExcludedRoots(urls -> urls.clear());
      assertRescanningLibraryContent(UNSURE, Collections.singletonList(classesDir), Collections.singletonList(sourcesDir));

      updateExcludedRoots(urls -> {
        urls.add(sourcesDir.getUrl());
      });
      assertRescanningLibraryContent(YES, Collections.singletonList(classesDir), Collections.emptyList());

      updateExcludedRoots(urls -> urls.clear());
      assertRescanningLibraryContent(YES, Collections.emptyList(), Collections.singletonList(sourcesDir));
    }

    private void updateExcludedRoots(Consumer<List<String>> urlsConsumer) {
      urlsConsumer.accept(excludedUrls);
      fireRootsChangedTotalRescan(RootsChangeRescanningInfo.NO_RESCAN_NEEDED);//to update DirectoryIndex
    }

    private void updateExcludedFromSdkRoots(Consumer<List<VirtualFile>> urlsConsumer) {
      urlsConsumer.accept(excludedFromSdk);
      fireRootsChangedTotalRescan(RootsChangeRescanningInfo.NO_RESCAN_NEEDED);//to update DirectoryIndex
    }

    private void assertRescanningProjectContent(ThreeState finishIndexingWithStatus, VirtualFile... roots) {
      DependenciesIndexedStatusService statusService = DependenciesIndexedStatusService.getInstance(getProject());
      @Nullable Pair<@NotNull Collection<? extends IndexableIteratorBuilder>, @NotNull StatusMark> statusPair;
      statusPair = statusService.getDeltaWithLastIndexedStatus();
      Collection<? extends IndexableIteratorBuilder> builders = statusPair.getFirst();
      List<VirtualFile> actualRoots = new ArrayList<>();
      for (IndexableIteratorBuilder builder : builders) {
        assertInstanceOf(builder, ModuleRootsFileBasedIteratorBuilder.class);
        List<VirtualFileUrl> urls = ((ModuleRootsFileBasedIteratorBuilder)builder).getFiles().getRoots();
        for (VirtualFileUrl url : urls) {
          VirtualFile file = VirtualFileUrls.getVirtualFile(url);
          actualRoots.add(file);
        }
      }
      assertContainsElements(actualRoots, roots);

      finishIndexing(finishIndexingWithStatus, statusPair.getSecond(), statusService);
    }

    private void assertRescanningSdkContent(ThreeState finishIndexingWithStatus, VirtualFile... roots) {
      DependenciesIndexedStatusService statusService = DependenciesIndexedStatusService.getInstance(getProject());
      @Nullable Pair<@NotNull Collection<? extends IndexableIteratorBuilder>, @NotNull StatusMark> statusPair;
      statusPair = statusService.getDeltaWithLastIndexedStatus();
      Collection<? extends IndexableIteratorBuilder> builders = statusPair.getFirst();
      List<VirtualFile> actualRoots = new ArrayList<>();
      for (IndexableIteratorBuilder builder : builders) {
        assertInstanceOf(builder, SdkIteratorBuilder.class);
        actualRoots.addAll(((SdkIteratorBuilder)builder).getRoots());
      }
      assertContainsElements(actualRoots, roots);

      finishIndexing(finishIndexingWithStatus, statusPair.getSecond(), statusService);
    }

    private void assertRescanningLibraryContent(ThreeState finishIndexingWithStatus,
                                                Collection<VirtualFile> roots,
                                                Collection<VirtualFile> sourceRoots) {
      DependenciesIndexedStatusService statusService = DependenciesIndexedStatusService.getInstance(getProject());
      @Nullable Pair<@NotNull Collection<? extends IndexableIteratorBuilder>, @NotNull StatusMark> statusPair;
      statusPair = statusService.getDeltaWithLastIndexedStatus();
      Collection<? extends IndexableIteratorBuilder> builders = statusPair.getFirst();
      List<VirtualFile> actualRoots = new ArrayList<>();
      List<VirtualFile> actualSourceRoots = new ArrayList<>();
      for (IndexableIteratorBuilder builder : builders) {
        assertInstanceOf(builder, LibraryIdIteratorBuilder.class);
        actualRoots.addAll(((LibraryIdIteratorBuilder)builder).getRoots());
        actualSourceRoots.addAll(((LibraryIdIteratorBuilder)builder).getSourceRoots());
      }
      assertContainsElements(actualRoots, roots);
      assertContainsElements(actualSourceRoots, sourceRoots);

      finishIndexing(finishIndexingWithStatus, statusPair.getSecond(), statusService);
    }
  }

  @NotNull
  protected Project getProject() {
    return projectModelRule.project;
  }

  protected StatusMark assertNothingToRescan() {
    DependenciesIndexedStatusService statusService = DependenciesIndexedStatusService.getInstance(getProject());
    Pair<Collection<? extends IndexableIteratorBuilder>, StatusMark> statusPair =
      statusService.getDeltaWithLastIndexedStatus();
    assertEmpty(statusPair.getFirst());
    return statusPair.getSecond();
  }

  protected void fireRootsChangedTotalRescan(RootsChangeRescanningInfo info) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(EmptyRunnable.getInstance(), info);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  protected void assertNothingToRescanAndFinishIndexing() {
    StatusMark mark = assertNothingToRescan();
    DependenciesIndexedStatusService.getInstance(getProject()).indexingFinished(true, mark);
  }

  protected static void finishIndexing(@NotNull ThreeState finishIndexingWithStatus,
                                       @NotNull StatusMark second,
                                       @NotNull DependenciesIndexedStatusService statusService) {
    switch (finishIndexingWithStatus) {
      case YES -> statusService.indexingFinished(true, second);
      case NO -> statusService.indexingFinished(false, second);
      case UNSURE -> {}
    }
  }
}
