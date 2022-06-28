// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration;

import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.util.containers.ContainerUtil;
import kotlin.KotlinVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.common.arguments.InternalArgument;
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.config.JvmTarget;
import org.jetbrains.kotlin.config.KotlinFacetSettings;
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout;
import org.jetbrains.kotlin.idea.facet.FacetUtilsKt;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.framework.JSLibraryKind;
import org.jetbrains.kotlin.idea.framework.JsLibraryStdDetectionUtil;
import org.jetbrains.kotlin.idea.framework.LibraryEffectiveKindProviderKt;
import org.jetbrains.kotlin.idea.project.PlatformKt;
import org.jetbrains.kotlin.idea.util.Java9StructureUtilKt;
import org.jetbrains.kotlin.idea.versions.KotlinRuntimeLibraryUtilKt;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.js.JsPlatforms;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleKt;
import org.jetbrains.kotlin.utils.PathUtil;
import org.jetbrains.plugins.groovy.config.GroovyHomeKind;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import static java.util.Collections.*;

@RunWith(JUnit38ClassRunner.class)
public class ConfigureKotlinTest extends AbstractConfigureKotlinTest {
    public void testNewLibrary() {
        doTestSingleJvmModule();

        String kotlinVersion = KotlinPluginLayout.getInstance().getStandaloneCompilerVersion().getArtifactVersion();

        ModuleRootManager.getInstance(getModule()).orderEntries().forEachLibrary(library -> {
            assertSameElements(
                    Arrays.stream(library.getRootProvider().getFiles(OrderRootType.CLASSES)).map(VirtualFile::getName).toArray(),
                    PathUtil.KOTLIN_JAVA_STDLIB_NAME + "-" + kotlinVersion + ".jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME + "-" + kotlinVersion + ".jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME + "-" + kotlinVersion + ".jar",
                    "kotlin-stdlib-common-" + kotlinVersion + ".jar",
                    "annotations-13.0.jar"
            );

            assertSameElements(
                    Arrays.stream(library.getRootProvider().getFiles(OrderRootType.SOURCES)).map(VirtualFile::getName).toArray(),
                    PathUtil.KOTLIN_JAVA_STDLIB_NAME + "-" + kotlinVersion + "-sources.jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME + "-" + kotlinVersion + "-sources.jar",
                    PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME + "-" + kotlinVersion + "-sources.jar",
                    "kotlin-stdlib-common-" + kotlinVersion + "-sources.jar",
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
        assertEquals(LanguageVersion.KOTLIN_1_0, PlatformKt.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_0, PlatformKt.getLanguageVersionSettings(myProject, null).getLanguageVersion());

        KotlinCommonCompilerArgumentsHolder.Companion.getInstance(myProject).update(settings -> {
            settings.setLanguageVersion(LanguageVersion.KOTLIN_1_6.getVersionString());
            return null;
        });

        assertEquals(LanguageVersion.KOTLIN_1_6, PlatformKt.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_6, PlatformKt.getLanguageVersionSettings(myProject, null).getLanguageVersion());
    }

    public void testProjectWithoutFacetWithRuntime11WithoutLanguageLevel() {
        assertEquals(LanguageVersion.KOTLIN_1_1, PlatformKt.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_1, PlatformKt.getLanguageVersionSettings(myProject, null).getLanguageVersion());
    }

    public void testProjectWithoutFacetWithRuntime11WithLanguageLevel10() {
        assertEquals(LanguageVersion.KOTLIN_1_0, PlatformKt.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(LanguageVersion.KOTLIN_1_0, PlatformKt.getLanguageVersionSettings(myProject, null).getLanguageVersion());
    }

    public void testProjectWithFacetWithRuntime11WithLanguageLevel10() {
        assertEquals(LanguageVersion.KOTLIN_1_0, PlatformKt.getLanguageVersionSettings(getModule()).getLanguageVersion());
        assertEquals(
                KotlinPluginLayout.getInstance().getStandaloneCompilerVersion().getLanguageVersion(),
                PlatformKt.getLanguageVersionSettings(myProject, null).getLanguageVersion()
        );
    }

    public void testJsLibraryVersion11() {
        Library jsRuntime = getFirstLibrary(myProject);
        IdeKotlinVersion version = JsLibraryStdDetectionUtil.INSTANCE.getJsLibraryStdVersion(jsRuntime, myProject);
        assertEquals(new KotlinVersion(1, 1, 0), version.getKotlinVersion());
    }

    public void testJsLibraryVersion106() {
        Library jsRuntime = getFirstLibrary(myProject);
        IdeKotlinVersion version = JsLibraryStdDetectionUtil.INSTANCE.getJsLibraryStdVersion(jsRuntime, myProject);
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
        assertEquals(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND, jsTest.get().getKind());
        assertEquals(JSLibraryKind.INSTANCE, LibraryEffectiveKindProviderKt.effectiveKind(jsTest.get(), myProject));
    }

    public void testJvmProjectWithV1FacetConfig() {
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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

    @SuppressWarnings("ConstantConditions")
    public void testJvmProjectWithV4FacetConfig() {
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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
        KotlinFacetSettings settings = KotlinFacetSettingsProvider.Companion.getInstance(myProject).getInitializedSettings(getModule());
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

        PsiJavaModule javaModule = Java9StructureUtilKt.findFirstPsiJavaModule(module);
        assertNotNull(javaModule);

        PsiRequiresStatement stdlibDirective =
                Java9StructureUtilKt.findRequireDirective(javaModule, JavaModuleKt.KOTLIN_STDLIB_MODULE_NAME);
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
            FacetUtilsKt.configureFacet(
                    facet,
                    IdeKotlinVersion.get("1.4.0"),
                    platform,
                    modelsProvider,
                    emptySet()
            );
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
        assertEquals(JvmPlatforms.INSTANCE.getJvm8(), PlatformKt.getPlatform(getModule()));
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
