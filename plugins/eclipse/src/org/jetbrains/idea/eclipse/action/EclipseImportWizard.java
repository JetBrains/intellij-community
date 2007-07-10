package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportWizard;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.*;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;
import org.jetbrains.idea.eclipse.util.PathUtil;
import org.jetbrains.idea.eclipse.util.Progress;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class EclipseImportWizard extends ProjectImportWizard
  implements EclipseProjectWizardContext, SelectImportedProjectsStep.Context<EclipseProjectModel> {

  static {
    Progress.defaultImpl = new Progress.Impl() {
      int myLimit = 0;
      int myCount;

      public void setPhaseName(String name) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null && name != null) {
          indicator.setText(name);
        }
      }

      public void startPhase(int limit) {
        myLimit = limit;
        myCount = 0;
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setIndeterminate(myLimit == 0);
        }
      }

      public void setText(String text) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText2(text);
          if (myLimit != 0) {
            indicator.setFraction((double)(++myCount) / myLimit);
          }
        }
      }
    };
  }

  public static class Parameters {
    public EclipseWorkspace workspace;
    public boolean linkConverted;
    public List<EclipseProjectModel> projectsToConvert = new ArrayList<EclipseProjectModel>();
    public boolean openModuleSettings;
    public EclipseToIdeaConverter.Options converterOptions = new EclipseToIdeaConverter.Options();
    public boolean updateCurrent;
    public Set<String> existingModuleNames;
  }

  private final Parameters parameters = new Parameters();

  private IdeaProjectModel ideaProjectModel;

  public String getName() {
    return EclipseBundle.message("eclipse.name");
  }

  @Nullable
  public String getRootDirectory() {
    return parameters.workspace == null ? null : parameters.workspace.getRoot();
  }

  public void setRootDirectory(final String path) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        parameters.workspace = EclipseWorkspace.load(path, new EclipseProjectReader.Options());
        Collections.sort(parameters.workspace.getProjects(), new Comparator<EclipseProjectModel>() {
          public int compare(EclipseProjectModel o1, EclipseProjectModel o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
          }
        });
      }
    }, EclipseBundle.message("eclipse.import.scanning"), false, null);
  }

  public List<EclipseProjectModel> getList() {
    return parameters.workspace.getProjects();
  }

  public boolean isMarked(final EclipseProjectModel element) {
    return !parameters.existingModuleNames.contains(element.getName());
  }

  public void setList(List<EclipseProjectModel> list) {
    parameters.projectsToConvert = list;
  }

  public boolean isOpenProjectSettingsAfter() {
    return parameters.openModuleSettings;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    parameters.openModuleSettings = on;
  }

  protected void initImport(final Project currentProject, final boolean updateCurrent) {
    super.initImport(currentProject, updateCurrent);

    parameters.workspace = null;
    parameters.updateCurrent = updateCurrent;
    parameters.existingModuleNames = new HashSet<String>();
    if (updateCurrent) {
      for (Module module : ModuleManager.getInstance(currentProject).getModules()) {
        parameters.existingModuleNames.add(module.getName());
      }
    }
  }

  protected AddModuleWizard.ModuleWizardStepFactory getStepsFactory(final Project currentProject, final boolean updateCurrent) {
    return new AddModuleWizard.ModuleWizardStepFactory() {
      public ModuleWizardStep[] createSteps(final WizardContext context) {
        return new ModuleWizardStep[]{new EclipseWorkspaceRootStep(context, EclipseImportWizard.this, parameters),
          new MySelectImportedProjectsStep(updateCurrent)};
      }
    };
  }

  protected boolean beforeProjectOpen(final Project currentProject, final Project dstProject) {
    final EclipseResolver eclipseResolver = new EclipseResolver() {
      final Map<String, String> existingModuleRoots = ClasspathStorage.getStorageRootMap(dstProject, null);

      @Nullable
      public String getProjectNameByPluginId(final String id) {
        return Util.getProjectNameByPluginId(parameters.projectsToConvert, id);
      }

      @Nullable
      public String getRootByName(final String name) {
        String root = Util.getRootByName(parameters.projectsToConvert, name);
        return root != null ? root : existingModuleRoots.get(name);
      }
    };

    final Ref<Exception> refEx = new Ref<Exception>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          ideaProjectModel = EclipseToIdeaConverter.convert(parameters.projectsToConvert, eclipseResolver,
                                                            EclipseClasspathStorageProvider.createLibraryResolver(dstProject),
                                                            parameters.converterOptions);
        }
        catch (ConversionException e) {
          refEx.set(e);
        }
        catch (IOException e) {
          refEx.set(e);
        }
      }
    }, EclipseBundle.message("eclipse.import.converting"), false, currentProject);

    if (!refEx.isNull()) {
      Messages.showErrorDialog(dstProject, refEx.get().getMessage(), getTitle());
      return false;
    }

    Set<String> undefinedMacros = getUndefinedMacros(ideaProjectModel);
    if (undefinedMacros.size() != 0 && !ProjectManagerImpl.showMacrosConfigurationDialog(dstProject, undefinedMacros)) {
      return false;
    }

    return true;
  }

  protected void afterProjectOpen(final Project project) {
    final Collection<String> libraries = new TreeSet<String>();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        convertModules(project, ideaProjectModel.getModules(), parameters.linkConverted, libraries);
        project.save();
      }
    });

    if (libraries.size() != 0) {
      StringBuffer message = new StringBuffer();
      for (String name : libraries) {
        message.append("\n");
        message.append(name);
      }
      Messages
        .showErrorDialog(project, EclipseBundle.message("eclipse.import.warning.undefinded.libraries", message.toString()), getTitle());
    }
  }

  public static void convertModules(final Project project,
                                    final Collection<IdeaModuleModel> modules,
                                    boolean link,
                                    final Collection<String> libraries) {
    final ModifiableModuleModel modifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel();
    EclipseProjectImporter.convertModules(modifiableModuleModel, modules, libraries);
    if (link) {
      for (IdeaModuleModel moduleModel : modules) {
        ClasspathStorage.setStorageType(modifiableModuleModel.findModuleByName(moduleModel.getName()), EclipseClasspathStorageProvider.ID);
      }
    }
    try {
      modifiableModuleModel.commit();
    }
    catch (ModuleCircularDependencyException e) {
      // not an error, actually not even ever thrown
    }
  }

  static Set<String> getUndefinedMacros(final IdeaProjectModel projectModel) {
    final PathMacros pathMacros = PathMacros.getInstance();
    final Set<String> undefinedMacros = new HashSet<String>();
    for (String macro : projectModel.getVariables()) {
      if (pathMacros.getValue(macro) == null) {
        undefinedMacros.add((macro));
      }
    }
    return undefinedMacros;
  }

  protected boolean canQuickImport(final VirtualFile file) {
    final String name = file.getName();
    return name.equals(EclipseXml.CLASSPATH_FILE) || name.equals(EclipseXml.PROJECT_FILE);
  }

  public boolean doQuickImport(VirtualFile file) {
    //noinspection ConstantConditions
    setRootDirectory(file.getParent().getPath());

    final List<EclipseProjectModel> projects = getList();
    if (projects.size() != 1) {
      return false;
    }
    setList(projects);
    myNewProjectName = projects.get(0).getName();
    return true;
  }

  private static final Icon ICON_CONFLICT = IconLoader.getIcon("/actions/cancel.png");

  private class MySelectImportedProjectsStep extends SelectImportedProjectsStep<EclipseProjectModel> {

    Set<String> duplicateNames;

    public MySelectImportedProjectsStep(final boolean updateCurrent) {
      super(EclipseImportWizard.this, updateCurrent);
      fileChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<EclipseProjectModel>() {
        public void elementMarkChanged(final EclipseProjectModel element, final boolean isMarked) {
          duplicateNames = null;
          fileChooser.repaint();
        }
      });
    }

    private boolean isInConflict(final EclipseProjectModel item) {
      calcDuplicates();
      return fileChooser.getMarkedElements().contains(item) && duplicateNames.contains(item.getName());
    }

    private void calcDuplicates() {
      if (duplicateNames == null) {
        duplicateNames = new HashSet<String>();
        Set<String> usedNames = new HashSet<String>();
        for (EclipseProjectModel model : fileChooser.getMarkedElements()) {
          if (!usedNames.add(model.getName())) {
            duplicateNames.add(model.getName());
          }
        }
      }
    }

    protected String getElementText(final EclipseProjectModel item) {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(item.getName());
      String relPath = PathUtil.getRelative(parameters.workspace.getRoot(), item.getRoot());
      if (!relPath.equals(".") && !relPath.equals(item.getName())) {
        stringBuilder.append(" (").append(relPath).append(")");
      }
      return stringBuilder.toString();
    }

    @Nullable
    protected Icon getElementIcon(final EclipseProjectModel item) {
      return isInConflict(item) ? ICON_CONFLICT : null;
    }

    public boolean validate() {
      calcDuplicates();
      return duplicateNames.isEmpty() && super.validate();
    }
  }
}
