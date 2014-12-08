package org.editorconfig.plugincomponents;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.editorconfig.Utils;
import org.editorconfig.annotations.EditorConfigAnnotator;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.core.EditorConfigException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SettingsProviderComponent implements ApplicationComponent {
  private EditorConfig editorConfig;

  public SettingsProviderComponent(FileTypeManager manager) {
    editorConfig = new EditorConfig();
    registerAnnotator(manager);
  }

  public void registerAnnotator(FileTypeManager manager) {
    final Set<String> languages = new HashSet<String>();
    final EditorConfigAnnotator annotator = new EditorConfigAnnotator();
    for (FileType type : manager.getRegisteredFileTypes()) {
      if (type instanceof LanguageFileType) {
        final Language lang = ((LanguageFileType)type).getLanguage();
        final String id = lang.getID();
        // don't add annotator for language twice
        // don't add annotator for languages not having own annotators - they may rely on parent annotators
        if (languages.contains(id) || (lang.getBaseLanguage() != null/* && LanguageAnnotators.INSTANCE.forKey(lang).isEmpty()*/)) continue;
        LanguageAnnotators.INSTANCE.addExplicitExtension(lang, annotator);
        languages.add(id);
      }
    }
  }

  public static SettingsProviderComponent getInstance() {
    return ServiceManager.getService(SettingsProviderComponent.class);
  }

  public List<OutPair> getOutPairs(Project project, String filePath) {
    final List<OutPair> outPairs;
    try {
      final Set<String> rootDirs = getRootDirs(project);
      outPairs = editorConfig.getProperties(filePath, rootDirs);
      return outPairs;
    }
    catch (EditorConfigException error) {
      Utils.invalidConfigMessage(project, error.getMessage(), "", filePath);
      return new ArrayList<OutPair>();
    }
  }

  public Set<String> getRootDirs(final Project project) {
    if (!Registry.is("editor.config.stop.at.project.root")) {
      return Collections.emptySet();
    }

    return CachedValuesManager.getManager(project).getCachedValue(project, new CachedValueProvider<Set<String>>() {
      @Nullable
      @Override
      public Result<Set<String>> compute() {
        final Set<String> dirs = new HashSet<String>();
        final VirtualFile projectBase = project.getBaseDir();
        dirs.add(project.getBasePath());
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
            if (!VfsUtilCore.isAncestor(projectBase, root, false)) {
              dirs.add(root.getPath());
            }
          }
        }
        return new Result<Set<String>>(dirs, ProjectRootModificationTracker.getInstance(project));
      }
    });
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "SettingsProviderComponent";
  }
}
