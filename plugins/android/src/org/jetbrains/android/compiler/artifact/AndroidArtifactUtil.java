package org.jetbrains.android.compiler.artifact;

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.Processor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
  public static AndroidFacet getPackagedFacet(Project project, Artifact artifact) {
    final Ref<AndroidFinalPackageElement> elementRef = Ref.create(null);
    final PackagingElementResolvingContext resolvingContext = ArtifactManager.getInstance(project).getResolvingContext();
    ArtifactUtil
      .processPackagingElements(artifact, AndroidFinalPackageElementType.getInstance(), new Processor<AndroidFinalPackageElement>() {
        public boolean process(AndroidFinalPackageElement e) {
          elementRef.set(e);
          return false;
        }
      }, resolvingContext, true);
    final AndroidFinalPackageElement element = elementRef.get();
    return element != null ? element.getFacet() : null;
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

  @Nullable
  public static String executeZipAlign(String zipAlignPath, File source, File destination) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(zipAlignPath);
    commandLine.addParameters("-f", "4", source.getAbsolutePath(), destination.getAbsolutePath());
    OSProcessHandler handler;
    try {
      handler = new OSProcessHandler(commandLine.createProcess(), "");
    }
    catch (ExecutionException e) {
      return e.getMessage();
    }
    final StringBuilder builder = new StringBuilder();
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        builder.append(event.getText());
      }
    });
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return exitCode != 0 ? builder.toString() : null;
  }
}
