package org.jetbrains.idea.eclipse.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.*;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;
import org.jetbrains.idea.eclipse.direct.IdeaXml;
import org.jetbrains.idea.eclipse.util.Progress;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class EclipseImportBuilder extends ProjectImportBuilder<EclipseProjectModel> implements EclipseProjectWizardContext {

  private static final Icon eclipseIcon = IconLoader.getIcon("/images/eclipse.gif");

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

      public boolean isCancelled() {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        return indicator != null && indicator.isCanceled();
      }
    };
  }

  public static class Parameters {
    public EclipseWorkspace workspace;
    public boolean linkConverted;
    public List<EclipseProjectModel> projectsToConvert = new ArrayList<EclipseProjectModel>();
    public boolean openModuleSettings;
    public EclipseToIdeaConverter.Options converterOptions = new EclipseToIdeaConverter.Options();
    public Set<String> existingModuleNames;
  }

  private Parameters parameters;

  private IdeaProjectModel ideaProjectModel;

  public String getName() {
    return EclipseBundle.message("eclipse.name");
  }

  public Icon getIcon() {
    return eclipseIcon;
  }

  @Nullable
  public String getRootDirectory() {
    return getParameters().workspace == null ? null : getParameters().workspace.getRoot();
  }

  public boolean setRootDirectory(final String path) {
    ProgressManager.getInstance().run(new Task.Modal(getCurrentProject(), EclipseBundle.message("eclipse.import.scanning"), true) {
      public void run(ProgressIndicator indicator) {
        getParameters().workspace = EclipseWorkspace.load(path, new EclipseProjectReader.Options());
        if (indicator.isCanceled()) {
          return;
        }
        Collections.sort(getParameters().workspace.getProjects(), new Comparator<EclipseProjectModel>() {
          public int compare(EclipseProjectModel o1, EclipseProjectModel o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
          }
        });
      }

      public void onCancel() {
        getParameters().workspace = null;
      }
    });

    return getParameters().workspace != null;
  }

  public List<EclipseProjectModel> getList() {
    return getParameters().workspace.getProjects();
  }

  public boolean isMarked(final EclipseProjectModel element) {
    return !getParameters().existingModuleNames.contains(element.getName());
  }

  public void setList(List<EclipseProjectModel> list) {
    getParameters().projectsToConvert = list;
  }

  public boolean isOpenProjectSettingsAfter() {
    return getParameters().openModuleSettings;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    getParameters().openModuleSettings = on;
  }

  public void cleanup() {
    super.cleanup();
    parameters = null;
  }

  public boolean validate(final Project currentProject, final Project dstProject) {
    final EclipseResolver eclipseResolver = new EclipseResolver() {
      final Map<String, String> existingModuleRoots = ClasspathStorage.getStorageRootMap(dstProject, null);

      @Nullable
      public String getProjectNameByPluginId(final String id) {
        return Util.getProjectNameByPluginId(getParameters().projectsToConvert, id);
      }

      @Nullable
      public String getRootByName(final String name) {
        String root = Util.getRootByName(getParameters().projectsToConvert, name);
        return root != null ? root : existingModuleRoots.get(name);
      }
    };

    final Ref<Exception> refEx = new Ref<Exception>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          ideaProjectModel = EclipseToIdeaConverter.convert(getParameters().projectsToConvert, eclipseResolver,
                                                            EclipseClasspathStorageProvider.createLibraryResolver(dstProject),
                                                            getParameters().converterOptions);
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

  public void commit(final Project project) {
    final Collection<String> libraries = new TreeSet<String>();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        convertModules(project, ideaProjectModel.getModules(), getParameters().linkConverted, getParameters().converterOptions, libraries);
        project.save();
      }
    });

    createEclipseLibrary(project, libraries, IdeaXml.ECLIPSE_LIBRARY);

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

  private void createEclipseLibrary(final Project project, final Collection<String> libraries, final String libraryName) {
    if (libraries.contains(libraryName)) {
      final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
        public Icon getOpenIcon(final VirtualFile virtualFile) {
          return looksLikeEclipse(virtualFile) ? eclipseIcon : super.getOpenIcon(virtualFile);
        }

        public Icon getClosedIcon(final VirtualFile virtualFile) {
          return looksLikeEclipse(virtualFile) ? eclipseIcon : super.getClosedIcon(virtualFile);
        }

        private boolean looksLikeEclipse(final VirtualFile virtualFile) {
          return virtualFile.findChild(".eclipseproduct") != null;
        }
      };
      fileChooserDescriptor.setTitle(EclipseBundle.message("eclipse.create.library.title"));
      fileChooserDescriptor.setDescription(EclipseBundle.message("eclipse.create.library.description", libraryName));
      final VirtualFile[] files = FileChooser.chooseFiles(project, fileChooserDescriptor);
      if (files.length == 1) {
        final VirtualFile pluginsDir = files[0].findChild("plugins");
        if (pluginsDir != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final LibraryTable table =
                LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(LibraryTablesRegistrar.APPLICATION_LEVEL, project);
              assert table != null;
              final LibraryTable.ModifiableModel tableModel = table.getModifiableModel();
              final Library library = tableModel.createLibrary(libraryName);
              final Library.ModifiableModel libraryModel = library.getModifiableModel();
              libraryModel.addJarDirectory(pluginsDir, true);
              libraryModel.commit();
              tableModel.commit();
            }
          });
          libraries.remove(libraryName);
        }
      }
    }
  }

  public static void convertModules(final Project project,
                                    final Collection<IdeaModuleModel> modules,
                                    boolean link,
                                    final EclipseToIdeaConverter.Options converterOptions,
                                    final Collection<String> libraries) {
    final ModifiableModuleModel modifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel();
    EclipseProjectImporter.convertModules(modifiableModuleModel, modules, converterOptions, libraries);
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

  public Parameters getParameters() {
    if (parameters == null) {
      parameters = new Parameters();
      parameters.existingModuleNames = new HashSet<String>();
      if (isUpdate()) {
        for (Module module : ModuleManager.getInstance(getCurrentProject()).getModules()) {
          parameters.existingModuleNames.add(module.getName());
        }
      }
    }
    return parameters;
  }
}
