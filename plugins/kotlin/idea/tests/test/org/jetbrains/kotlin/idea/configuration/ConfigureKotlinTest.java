// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration;

import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiRequiresStatement;
import kotlin.KotlinVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.common.arguments.InternalArgument;
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.config.*;
import org.jetbrains.kotlin.idea.base.facet.platform.TargetPlatformDetectorUtils;
import org.jetbrains.kotlin.idea.base.indices.JavaIndicesUtils;
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind;
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility;
import org.jetbrains.kotlin.idea.base.platforms.LibraryEffectiveKindProvider;
import org.jetbrains.kotlin.idea.base.projectStructure.LanguageVersionSettingsProviderUtils;
import org.jetbrains.kotlin.idea.base.psi.JavaPsiUtils;
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout;
import org.jetbrains.kotlin.idea.facet.FacetUtilsKt;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.macros.KotlinBundledUsageDetector;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.js.JsPlatforms;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleKt;
import org.jetbrains.kotlin.utils.PathUtil;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@RunWith(JUnit38ClassRunner.class)
public class ConfigureKotlinTest extends AbstractConfigureKotlinTest {
    public void testNewLibrary() {
        doTestSingleJvmModule();

        String kotlinVersion = KotlinPluginLayout.getStandaloneCompilerVersion().getArtifactVersion();

        ModuleRootManager.getInstance(getModule()).orderEntries().forEachLibrary(library -> {
            assertSameElements(
                    Arrays.stream(library.getRootProvider().getFiles(OrderRootType.CLASSES)).map(VirtualFile::getName).toArray(),
                    PathUtil.KOTLIN_JAVA_STDLIB_NAME + "-" + kotlinVersion + ".jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME + "-" + kotlinVersion + ".jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME + "-" + kotlinVersion + ".jar",
                    "annotations-13.0.jar"
            );

            assertSameElements(
                    Arrays.stream(library.getRootProvider().getFiles(OrderRootType.SOURCES)).map(VirtualFile::getName).toArray(),
                    PathUtil.KOTLIN_JAVA_STDLIB_NAME + "-" + kotlinVersion + "-sources.jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME + "-" + kotlinVersion + "-sources.jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME + "-" + kotlinVersion + "-sources.jar",
                    "annotations-13.0-sources.jar"
            );

            return true;
        });
    }

    public void testLibraryWithoutPaths() {
        doTestSingleJvmModule();
    }

    public void testStdlibDoesntHaveCompileScope() {
        doTestSingleJvmModule();
    }

    public void testTwoModules() {
        Module[] modules = getModules();
        for (Module module : modules) {
            assertNotConfigured(module, getJvmConfigurator());
            configure(module, getJvmConfigurator());
            assertConfigured(module, getJvmConfigurator());
        }
    }

    public void testKotlinBundledUsedInLibraryClasses() {
        assertTrue(KotlinBundledUsageDetector.isKotlinBundledPotentiallyUsedInLibraries(myProject));
    }

    public void testKotlinBundledUsedInModuleLibraryClasses() {
        assertTrue(KotlinBundledUsageDetector.isKotlinBundledPotentiallyUsedInLibraries(myProject));
    }

    public void testKotlinBundledUsedInUnusedLibraryClasses() {
        // it is true because [org.jetbrains.kotlin.idea.macros.KotlinBundledUsageDetector.ModelChangeListener] triggers once per project
        // we must have [org.jetbrains.kotlin.idea.macros.KotlinBundledUsageDetector.MyStartupActivity] to process reopened projects
        assertTrue(KotlinBundledUsageDetector.isKotlinBundledPotentiallyUsedInLibraries(myProject));
    }

    public void testNewLibrary_js() {
        doTestSingleJsModule();
    }

    public void testJsLibraryWithoutPaths_js() {
        doTestSingleJsModule();
    }

    public void testJsLibraryWrongKind() {
        assertProperlyConfigured(getModule(), getJsConfigurator());
        assertEquals(1, ModuleRootManager.getInstance(getModule()).orderEntries().process(new LibraryCountingRootPolicy(), 0).intValue());
    }

    public void testProjectWithoutFacetWithRuntime106WithoutLanguageLevel() {
        assertEquals(LanguageVersion.KOTLIN_1_0, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_0, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(myProject).getLanguageVersion());

        KotlinCommonCompilerArgumentsHolder.Companion.getInstance(myProject).update(settings -> {
            settings.setLanguageVersion(LanguageVersion.KOTLIN_1_6.getVersionString());
            return null;
        });

        // Emulate project root change, as after changing Kotlin language settings in the preferences
        WriteAction.runAndWait(() -> {
            ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.NO_RESCAN_NEEDED);
        });

        assertEquals(LanguageVersion.KOTLIN_1_6, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_6, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(myProject).getLanguageVersion());
    }

    public void testProjectWithoutFacetWithRuntime11WithoutLanguageLevel() {
        assertEquals(LanguageVersion.KOTLIN_1_1, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_1, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(myProject).getLanguageVersion());
    }

    public void testProjectWithoutFacetWithRuntime11WithLanguageLevel10() {
        assertEquals(LanguageVersion.KOTLIN_1_0, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_0, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(myProject).getLanguageVersion());
    }

    public void testProjectWithFacetWithRuntime11WithLanguageLevel10() {
        assertEquals(LanguageVersion.KOTLIN_1_0, LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(
                KotlinPluginLayout.getStandaloneCompilerVersion().getLanguageVersion(),
                LanguageVersionSettingsProviderUtils.getLanguageVersionSettings(myProject).getLanguageVersion()
        );
    }

    public void testJsLibraryVersion11() {
        Library jsRuntime = getFirstLibrary(myProject);
        IdeKotlinVersion version = KotlinJavaScriptStdlibDetectorFacility.INSTANCE.getStdlibVersion(myProject, jsRuntime);
        assertEquals(new KotlinVersion(1, 1, 0), version.getKotlinVersion());
    }

    public void testJsLibraryVersion106() {
        Library jsRuntime = getFirstLibrary(myProject);
        IdeKotlinVersion version = KotlinJavaScriptStdlibDetectorFacility.INSTANCE.getStdlibVersion(myProject, jsRuntime);
        assertEquals(new KotlinVersion(1, 0, 6), version.getKotlinVersion());
    }

    public void testMavenProvidedTestJsKind() {
        Ref<LibraryEx> jsTest = new Ref<>();
        OrderEnumerator.orderEntries(myProject).forEachLibrary((library) -> {
            if (library.getName().contains("kotlin-test-js")) {
                jsTest.set((LibraryEx) library);
                return false;
            }
            return true;
        });

        LibraryEffectiveKindProvider effectiveKindProvider = myProject.getService(LibraryEffectiveKindProvider.class);

        assertEquals(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND, jsTest.get().getKind());
        assertEquals(KotlinJavaScriptLibraryKind.INSTANCE, effectiveKindProvider.getEffectiveKind(jsTest.get()));
    }

    public void testJvmProjectWithV1FacetConfig() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        K2JVMCompilerArguments arguments = (K2JVMCompilerArguments) settings.getCompilerArguments();
        assertFalse(settings.getUseProjectSettings());
        assertEquals(LanguageVersion.KOTLIN_1_1, settings.getLanguageLevel());
        assertEquals(LanguageVersion.KOTLIN_1_0, settings.getApiLevel());
        assertEquals(JvmPlatforms.INSTANCE.getJvm8(), settings.getTargetPlatform());
        assertEquals("1.1", arguments.getLanguageVersion());
        assertEquals("1.0", arguments.getApiVersion());
        assertEquals("1.7", arguments.getJvmTarget());
        assertEquals("-version -Xallow-kotlin-package -Xskip-metadata-version-check",
                     settings.getCompilerSettings().getAdditionalArguments());
    }

    public void testJsProjectWithV1FacetConfig() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        K2JSCompilerArguments arguments = (K2JSCompilerArguments) settings.getCompilerArguments();
        assertFalse(settings.getUseProjectSettings());
        assertEquals(LanguageVersion.KOTLIN_1_1, settings.getLanguageLevel());
        assertEquals(LanguageVersion.KOTLIN_1_0, settings.getApiLevel());
        assertEquals(JsPlatforms.INSTANCE.getDefaultJsPlatform(), settings.getTargetPlatform());
        assertEquals("1.1", arguments.getLanguageVersion());
        assertEquals("1.0", arguments.getApiVersion());
        assertEquals("amd", arguments.getModuleKind());
        assertEquals("-version -meta-info", settings.getCompilerSettings().getAdditionalArguments());
    }

    public void testJvmProjectWithV2FacetConfig() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        K2JVMCompilerArguments arguments = (K2JVMCompilerArguments) settings.getCompilerArguments();
        assertFalse(settings.getUseProjectSettings());
        assertEquals(LanguageVersion.KOTLIN_1_1, settings.getLanguageLevel());
        assertEquals(LanguageVersion.KOTLIN_1_0, settings.getApiLevel());
        assertEquals(JvmPlatforms.INSTANCE.getJvm8(), settings.getTargetPlatform());
        assertEquals("1.1", arguments.getLanguageVersion());
        assertEquals("1.0", arguments.getApiVersion());
        assertEquals("1.7", arguments.getJvmTarget());
        assertEquals("-version -Xallow-kotlin-package -Xskip-metadata-version-check",
                     settings.getCompilerSettings().getAdditionalArguments());
    }

    public void testJsProjectWithV2FacetConfig() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        K2JSCompilerArguments arguments = (K2JSCompilerArguments) settings.getCompilerArguments();
        assertFalse(settings.getUseProjectSettings());
        assertEquals(LanguageVersion.KOTLIN_1_1, settings.getLanguageLevel());
        assertEquals(LanguageVersion.KOTLIN_1_0, settings.getApiLevel());
        assertEquals(JsPlatforms.INSTANCE.getDefaultJsPlatform(), settings.getTargetPlatform());
        assertEquals("1.1", arguments.getLanguageVersion());
        assertEquals("1.0", arguments.getApiVersion());
        assertEquals("amd", arguments.getModuleKind());
        assertEquals("-version -meta-info", settings.getCompilerSettings().getAdditionalArguments());
    }

    public void testJvmProjectWithV3FacetConfig() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        K2JVMCompilerArguments arguments = (K2JVMCompilerArguments) settings.getCompilerArguments();
        assertFalse(settings.getUseProjectSettings());
        assertEquals(LanguageVersion.KOTLIN_1_1, settings.getLanguageLevel());
        assertEquals(LanguageVersion.KOTLIN_1_0, settings.getApiLevel());
        assertEquals(JvmPlatforms.INSTANCE.getJvm8(), settings.getTargetPlatform());
        assertEquals("1.1", arguments.getLanguageVersion());
        assertEquals("1.0", arguments.getApiVersion());
        assertEquals("1.7", arguments.getJvmTarget());
        assertEquals("-version -Xallow-kotlin-package -Xskip-metadata-version-check",
                     settings.getCompilerSettings().getAdditionalArguments());
    }

    public void testJvmProjectWithV4FacetConfig() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        K2JVMCompilerArguments arguments = (K2JVMCompilerArguments) settings.getCompilerArguments();
        assertFalse(settings.getUseProjectSettings());
        assertEquals(LanguageVersion.KOTLIN_1_4, settings.getLanguageLevel());
        assertEquals(LanguageVersion.KOTLIN_1_2, settings.getApiLevel());
        assertEquals(JvmPlatforms.INSTANCE.getJvm8(), settings.getTargetPlatform());
        assertEquals("1.4", arguments.getLanguageVersion());
        assertEquals("1.2", arguments.getApiVersion());
        assertEquals("1.8", arguments.getJvmTarget());
        assertEquals("-version -Xallow-kotlin-package -Xskip-metadata-version-check", settings.getCompilerSettings().getAdditionalArguments());
    }

    public void testJvmProjectWithJvmTarget11() {
        IKotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
        assertEquals(JvmPlatforms.INSTANCE.jvmPlatformByTargetVersion(JvmTarget.JVM_11), settings.getTargetPlatform());
    }

    public void testImplementsDependency() {
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);

        Module module1 = moduleManager.findModuleByName("module1");
        assert module1 != null;

        Module module2 = moduleManager.findModuleByName("module2");
        assert module2 != null;

        assertEquals(emptyList(), KotlinFacet.Companion.get(module1).getConfiguration().getSettings().getImplementedModuleNames());
        assertEquals(singletonList("module1"),
                     KotlinFacet.Companion.get(module2).getConfiguration().getSettings().getImplementedModuleNames());
    }

    public void testJava9WithModuleInfo() {
        configureFacetAndCheckJvm(JvmTarget.JVM_9);
    }

    public void testJava9WithModuleInfoWithStdlibAlready() {
        checkAddStdlibModule();
    }

    public void testProjectWithFreeArgs() {
        assertEquals(singletonList("true"),
                     KotlinCommonCompilerArgumentsHolder.Companion.getInstance(myProject).getSettings().getFreeArgs());
    }

    public void testProjectWithInternalArgs() {
        List<InternalArgument> internalArguments =
                KotlinCommonCompilerArgumentsHolder.Companion.getInstance(myProject).getSettings().getInternalArguments();
        assertEquals(
                0,
                internalArguments.size()
        );
    }

    private void checkAddStdlibModule() {
        doTestSingleJvmModule();

        Module module = getModule();
        Sdk moduleSdk = ModuleRootManager.getInstance(getModule()).getSdk();
        assertNotNull("Module SDK is not defined", moduleSdk);

        PsiJavaModule javaModule = JavaIndicesUtils.findModuleInfoFile(myProject, module.getModuleScope());
        assertNotNull(javaModule);

        PsiRequiresStatement stdlibDirective = JavaPsiUtils.findRequireDirective(javaModule, JavaModuleKt.KOTLIN_STDLIB_MODULE_NAME);
        assertNotNull("Require directive for " + JavaModuleKt.KOTLIN_STDLIB_MODULE_NAME + " is expected",
                      stdlibDirective);

        long numberOfStdlib = StreamSupport.stream(javaModule.getRequires().spliterator(), false)
                .filter((statement) -> JavaModuleKt.KOTLIN_STDLIB_MODULE_NAME.equals(statement.getModuleName()))
                .count();

        assertEquals("Only one standard library directive is expected", 1, numberOfStdlib);
    }

    private void configureFacetAndCheckJvm(JvmTarget jvmTarget) {
      IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getProject());
        try {
            KotlinFacet facet = FacetUtilsKt.getOrCreateFacet(getModule(), modelsProvider, false, null, false);
            TargetPlatform platform = JvmPlatforms.INSTANCE.jvmPlatformByTargetVersion(jvmTarget);
            FacetUtilsKt.configureFacet(facet, IdeKotlinVersion.get("1.4.0"), platform, modelsProvider);
            assertEquals(platform, facet.getConfiguration().getSettings().getTargetPlatform());
            assertEquals(jvmTarget.getDescription(),
                         ((K2JVMCompilerArguments) facet.getConfiguration().getSettings().getCompilerArguments()).getJvmTarget());
        } finally {
            modelsProvider.dispose();
        }
    }

    public void testJvm8InProjectJvm6InModule() {
        configureFacetAndCheckJvm(JvmTarget.JVM_1_6);
    }

    public void testJvm6InProjectJvm8InModule() {
        configureFacetAndCheckJvm(JvmTarget.JVM_1_8);
    }

    public void testProjectWithoutFacetWithJvmTarget18() {
        assertEquals(JvmPlatforms.INSTANCE.getJvm8(), TargetPlatformDetectorUtils.getPlatform(getModule()));
    }

    private static Library getFirstLibrary(@NotNull Project project) {
        Ref<Library> ref = new Ref<>();
        OrderEnumerator.orderEntries(project).forEachLibrary((library) ->{
            ref.set(library);
            return true;
        });
        return ref.get();
    }

    private static class LibraryCountingRootPolicy extends RootPolicy<Integer> {
        @Override
        public Integer visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry, Integer value) {
            return value + 1;
        }
    }
}
