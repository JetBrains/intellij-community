// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.plugincomponents;

import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValueImpl;
import org.editorconfig.EditorConfigNotifier;
import org.editorconfig.EditorConfigRegistry;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.ConfigEncodingManager;
import org.editorconfig.configmanagement.EditorConfigEncodingCache;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.core.ParserCallback;
import org.editorconfig.core.ParsingException;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Service
public final class SettingsProviderComponent extends SimpleModificationTracker {
  private static final Key<CachedValue<List<OutPair>>> CACHED_PAIRS = Key.create("editorconfig.cached.pairs");
  public static final String ERROR = "___error___";
  private final EditorConfig editorConfig;

  private final static Logger LOG = Logger.getInstance(SettingsProviderComponent.class);

  public SettingsProviderComponent() {
    editorConfig = new EditorConfig();
  }

  public static SettingsProviderComponent getInstance() {
    return ApplicationManager.getApplication().getService(SettingsProviderComponent.class);
  }

  public List<OutPair> getOutPairs(Project project, VirtualFile file) {
    return getOutPairs(project, file, null);
  }

  public List<OutPair> getOutPairs(Project project, VirtualFile file, @Nullable ParserCallback callback) {
    final String filePath = Utils.getFilePath(project, file);
    if (filePath == null) return Collections.emptyList();
    final UserDataHolder dataHolder = CodeStyleCachingService.getInstance(project).getDataHolder(file);
    if (dataHolder == null) return Collections.emptyList();
    CachedValue<List<OutPair>> cache = dataHolder.getUserData(CACHED_PAIRS);
    if (cache == null) {
      final Set<String> rootDirs = getRootDirs(project);
      cache = new CachedValueImpl<>(new CachedPairsProvider(file, filePath, project, rootDirs, callback));
      dataHolder.putUserData(CACHED_PAIRS, cache);
    }
    return cache.getValue();
  }

  public Set<String> getRootDirs(final Project project) {
    if (!EditorConfigRegistry.shouldStopAtProjectRoot()) {
      return Collections.emptySet();
    }

    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      final Set<String> dirs = new HashSet<>();
      @SuppressWarnings("deprecation")
      final VirtualFile projectBase = project.getBaseDir();
      if (projectBase != null) {
        dirs.add(project.getBasePath());
        ReadAction.run(() -> {
          for (Module module : ModuleManager.getInstance(project).getModules()) {
            for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
              if (!VfsUtilCore.isAncestor(projectBase, root, false)) {
                dirs.add(root.getPath());
              }
            }
          }
        });
      }
      dirs.add(PathManager.getConfigPath());
      return new CachedValueProvider.Result<>(dirs, ProjectRootModificationTracker.getInstance(project));
    });
  }

  private class CachedPairsProvider implements CachedValueProvider<List<OutPair>> {
    private final @NotNull  VirtualFile    myFile;
    private final @NotNull  String         myFilePath;
    private final @NotNull  Set<String>    myRootDirs;
    private final @Nullable ParserCallback myCallback;
    private final @NotNull  Project        myProject;

    private final @NotNull SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

    private @Nullable Future<List<OutPair>> myFuture;
    private long myParentModificationCount;

    private CachedPairsProvider(@NotNull VirtualFile file,
                                @NotNull String filePath,
                                @NotNull Project project,
                                @NotNull Set<String> rootDirs,
                                @Nullable ParserCallback callback) {
      myFile = file;
      myFilePath = filePath;
      myProject = project;
      myRootDirs = rootDirs;
      myCallback = callback;
    }

    @Override
    public @Nullable Result<List<OutPair>> compute() {
      try {
        List<OutPair> outPairs = getProperties();
        return CachedValueProvider.Result.create(outPairs, SettingsProviderComponent.this, myModificationTracker);
      }
      catch (Exception error) {
        ArrayList<OutPair> errorResult = new ArrayList<>();
        errorResult.add(new OutPair(ERROR, error.getMessage()));
        return CachedValueProvider.Result.create(errorResult, SettingsProviderComponent.this);
      }
    }

    private List<OutPair> getProperties()
      throws ExecutionException, InterruptedException, EditorConfigException {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return editorConfig.getProperties(myFilePath, myRootDirs, myCallback);
      }
      long currParentModificationCount = SettingsProviderComponent.this.getModificationCount();
      if (myFuture == null || myParentModificationCount != currParentModificationCount) {
        myParentModificationCount = currParentModificationCount;
        if (myFuture != null && !myFuture.isDone()) {
          myFuture.cancel(true);
        }
        myFuture =
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<OutPair> pairs = new ArrayList<>();
            Document document = FileDocumentManager.getInstance().getCachedDocument(myFile);
            long docStamp = document != null ? document.getModificationStamp() : -1;
            try {
              pairs.addAll(editorConfig.getProperties(myFilePath, myRootDirs, myCallback));
              if (!pairs.isEmpty()) {
                if (document != null && document.getModificationStamp() != docStamp && !Utils.isEditorConfigFile(myFile)) {
                  LOG.warn("Document has changed for file " + myFilePath);
                  EditorConfigNotifier.error(myProject, myFilePath, EditorConfigBundle.message("error.message.cannot.apply", myFilePath));
                }
                else {
                  handleSettingsComputed(myFile, pairs);
                }
              }
            }
            catch (ParsingException pe) { // .editorconfig may be temporarily incorrect
              pairs.add(new OutPair(ERROR, pe.getMessage()));
            }
            myModificationTracker.incModificationCount();
            return pairs;
          });
      }
      else {
        if (myFuture.isDone()) {
          return myFuture.get();
        }
      }
      return Collections.emptyList();
    }
  }

  private static void handleSettingsComputed(@NotNull VirtualFile file, @NotNull List<OutPair> pairs) {
    final String encoding = Utils.configValueForKey(pairs, ConfigEncodingManager.charsetKey);
    if (encoding != null && !Utils.isEditorConfigFile(file)) {
      EditorConfigEncodingCache.getInstance().setEncoding(file, encoding);
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (editor.isDisposed() || editor.getDocument() != document) continue;
        if (encoding != null && !Utils.isEditorConfigFile(file)) {
          FileDocumentManager.getInstance().reloadFiles(file);
        }
        else {
          EditorSettingsManager.applyEditorSettings(editor);
          ((EditorEx)editor).reinitSettings();
        }
      }
    });
  }
}
