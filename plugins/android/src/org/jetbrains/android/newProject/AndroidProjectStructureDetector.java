package org.jetbrains.android.newProject;

import com.android.SdkConstants;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProjectStructureDetector extends ProjectStructureDetector {
  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir,
                                               @NotNull File[] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    for (File child : children) {
      if (child.isFile() && SdkConstants.FN_ANDROID_MANIFEST_XML.equals(child.getName())) {
        result.add(new AndroidProjectRoot(dir));
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  @Override
  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots,
                                    @NotNull ProjectDescriptor projectDescriptor,
                                    @NotNull ProjectFromSourcesBuilder builder) {
    final List<File> existingRoots = new ArrayList<File>();

    for (ProjectStructureDetector detector : ProjectStructureDetector.EP_NAME.getExtensions()) {
      if (detector != this) {
        for (DetectedProjectRoot root : builder.getProjectRoots(detector)) {
          existingRoots.add(root.getDirectory());
        }
      }
    }
    final List<ModuleDescriptor> modules = new ArrayList<ModuleDescriptor>();

    for (DetectedProjectRoot root : roots) {
      final File dir = root.getDirectory();
      boolean javaSrcRootInside = false;

      for (File javaSourceRoot : existingRoots) {
        if (FileUtil.isAncestor(dir, javaSourceRoot, false)) {
          javaSrcRootInside = true;
        }
      }

      if (!javaSrcRootInside) {
        modules.add(new ModuleDescriptor(
          root.getDirectory(), JavaModuleType.getModuleType(),
          Collections.<DetectedProjectRoot>emptyList()));
      }
    }
    projectDescriptor.setModules(modules);
  }

  private static class AndroidProjectRoot extends DetectedProjectRoot {

    public AndroidProjectRoot(@NotNull File directory) {
      super(directory);
    }

    @NotNull
    @Override
    public String getRootTypeName() {
      return "Android";
    }
  }
}
