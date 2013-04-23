package org.jetbrains.jps.android;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.builder.AndroidPackagingBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.impl.JpsAndroidFinalPackageElement;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuilderService;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFinalPackageElementBuilder extends LayoutElementBuilderService<JpsAndroidFinalPackageElement> {
  public AndroidFinalPackageElementBuilder() {
    super(JpsAndroidFinalPackageElement.class);
  }

  @Override
  public void generateInstructions(JpsAndroidFinalPackageElement element,
                                   ArtifactCompilerInstructionCreator instructionCreator,
                                   ArtifactInstructionsBuilderContext builderContext) {
    final String apkPath = getApkPath(element);

    if (apkPath != null) {
      instructionCreator.addExtractDirectoryInstruction(new File(apkPath), "");
    }
  }

  @Override
  public Collection<? extends BuildTarget<?>> getDependencies(@NotNull JpsAndroidFinalPackageElement element, TargetOutputIndex outputIndex) {
    final JpsModule module = element.getModuleReference().resolve();
    return module != null
           ? Collections.singletonList(new AndroidPackagingBuildTarget(module))
           : Collections.<BuildTarget<?>>emptyList();
  }

  @Nullable
  private static String getApkPath(JpsAndroidFinalPackageElement element) {
    final JpsModuleReference ref = element.getModuleReference();
    final JpsModule module = ref != null ? ref.resolve() : null;

    if (module == null) {
      return null;
    }
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

    if (extension == null) {
      return null;
    }
    return getApkPath(extension);
  }

  @Nullable
  public static String getApkPath(@NotNull JpsAndroidModuleExtension extension) {
    final File moduleOutputDir = JpsJavaExtensionService.getInstance().getOutputDirectory(extension.getModule(), false);

    if (moduleOutputDir == null) {
      return null;
    }
    final String apkPath = AndroidJpsUtil.getApkPath(extension, moduleOutputDir);

    return apkPath != null
           ? FileUtil.toSystemIndependentName(apkPath)
           : null;
  }
}
