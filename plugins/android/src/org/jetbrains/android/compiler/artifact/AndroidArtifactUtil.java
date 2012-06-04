package org.jetbrains.android.compiler.artifact;

import com.intellij.CommonBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.Processor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidArtifactUtil {
  private AndroidArtifactUtil() {
  }

  public static boolean containsAndroidPackage(ArtifactEditorContext editorContext, Artifact artifact) {
    return !ArtifactUtil
      .processPackagingElements(artifact, AndroidFinalPackageElementType.getInstance(), new Processor<AndroidFinalPackageElement>() {
        public boolean process(AndroidFinalPackageElement e) {
          return false;
        }
      }, editorContext, true);
  }

  @Nullable
  public static AndroidFacet chooseAndroidApplicationModule(@NotNull Project project, @NotNull List<Module> modules) {
    final ChooseModulesDialog dialog = new ChooseModulesDialog(project, modules, "Select Module",
                                                               "Selected Android application module will be included in the created artifact with all dependencies");
    dialog.setSingleSelectionMode();
    dialog.show();
    final List<Module> selected = dialog.getChosenElements();
    if (selected.isEmpty()) {
      return null;
    }
    assert selected.size() == 1;
    final Module module = selected.get(0);
    final String moduleName = module.getName();

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      final String message = "Cannot find Android facet for module " + moduleName;
      Messages.showErrorDialog(project, message, CommonBundle.getErrorTitle());
      return null;
    }
    return facet;
  }
}
