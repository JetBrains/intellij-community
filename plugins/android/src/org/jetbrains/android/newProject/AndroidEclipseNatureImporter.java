package org.jetbrains.android.newProject;

import com.android.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFrameworkDetector;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.importWizard.EclipseNatureImporter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidEclipseNatureImporter extends EclipseNatureImporter {

  private static final Set<String> ADT_INTERNAL_LIBS = new HashSet<String>(Arrays.asList(
    "com.android.ide.eclipse.adt.ANDROID_FRAMEWORK",
    "com.android.ide.eclipse.adt.LIBRARIES"));

  @NotNull
  @Override
  public String getNatureName() {
    return "com.android.ide.eclipse.adt.AndroidNature";
  }

  @Override
  public Set<String> getProvidedCons() {
    return ADT_INTERNAL_LIBS;
  }

  @Override
  public void doImport(@NotNull Project project, @NotNull List<Module> modules) {
    for (Module module : modules) {
      final VirtualFile contentRoot = chooseMainContentRoot(module);

      if (contentRoot == null) {
        AndroidUtils.reportImportErrorToEventLog("Cannot find content root containing " +
                                                 SdkConstants.FN_ANDROID_MANIFEST_XML + " file", module.getName());
        continue;
      }
      final AndroidFacet facet = AndroidUtils.addAndroidFacetInWriteAction(module, contentRoot, false);
      final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
      AndroidFrameworkDetector.doImportSdkAndFacetConfiguration(facet, modifiableModel);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          modifiableModel.commit();
        }
      });
    }
    ImportDependenciesUtil.doImportDependencies(project, modules, true);
  }

  @Nullable
  private static VirtualFile chooseMainContentRoot(@NotNull Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();

    if (roots.length == 0) {
      return null;
    }
    if (roots.length == 1) {
      return roots[0];
    }

    for (VirtualFile root : roots) {
      if (root.findChild(SdkConstants.FN_ANDROID_MANIFEST_XML) != null) {
        return root;
      }
    }
    return null;
  }
}
