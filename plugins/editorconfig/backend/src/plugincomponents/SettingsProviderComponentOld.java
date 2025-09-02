//// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//package org.editorconfig.plugincomponents;
//
//import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.application.PathManager;
//import com.intellij.openapi.application.ReadAction;
//import com.intellij.openapi.components.Service;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.module.Module;
//import com.intellij.openapi.module.ModuleManager;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.roots.ModuleRootManager;
//import com.intellij.openapi.roots.ProjectRootModificationTracker;
//import com.intellij.openapi.util.Key;
//import com.intellij.openapi.util.ModificationTracker;
//import com.intellij.openapi.util.SimpleModificationTracker;
//import com.intellij.openapi.util.UserDataHolder;
//import com.intellij.openapi.vfs.VfsUtilCore;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.psi.util.CachedValue;
//import com.intellij.psi.util.CachedValueProvider;
//import com.intellij.psi.util.CachedValuesManager;
//import com.intellij.util.CachedValueImpl;
//import org.editorconfig.EditorConfigRegistry;
//import org.editorconfig.Utils;
//import org.editorconfig.core.EditorConfig;
//import org.editorconfig.core.EditorConfig.OutPair;
//import org.editorconfig.core.EditorConfigException;
//import org.editorconfig.core.ParserCallback;
//import org.editorconfig.core.ParsingException;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import java.util.*;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//
//@Service
//public final class SettingsProviderComponentOld extends SimpleModificationTracker {
//  private static final Key<CachedValue<List<OutPair>>> CACHED_PAIRS = Key.create("editorconfig.cached.pairs");
//  public static final String ERROR = "___error___";
//  private final EditorConfig editorConfig;
//
//  private final static Logger LOG = Logger.getInstance(SettingsProviderComponentOld.class);
//
//  private final static long TIMEOUT = 3; // Seconds
//
//  public SettingsProviderComponentOld() {
//    editorConfig = new EditorConfig();
//  }
//
//  public static SettingsProviderComponentOld getInstance() {
//    return ApplicationManager.getApplication().getService(SettingsProviderComponentOld.class);
//  }
//
//  public List<OutPair> getOutPairs(Project project, VirtualFile file) {
//    return getOutPairs(project, file, null);
//  }
//
//  public List<OutPair> getOutPairs(Project project, VirtualFile file, @Nullable ParserCallback callback) {
//    final String filePath = Utils.getFilePath(project, file);
//    if (filePath == null) return Collections.emptyList();
//    final UserDataHolder dataHolder = CodeStyleCachingService.getInstance(project).getDataHolder(file);
//    if (dataHolder == null) return Collections.emptyList();
//    CachedValue<List<OutPair>> cache = dataHolder.getUserData(CACHED_PAIRS);
//    if (cache == null) {
//      final Set<String> rootDirs = getRootDirs(project);
//      cache = new CachedValueImpl<>(new CachedPairsProvider(filePath, rootDirs, callback));
//      dataHolder.putUserData(CACHED_PAIRS, cache);
//    }
//    return cache.getValue();
//  }
//
//  // TODO very nice
//  public Set<String> getRootDirs(final Project project) {
//    if (!EditorConfigRegistry.shouldStopAtProjectRoot()) {
//      return Collections.emptySet();
//    }
//
//    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
//      final Set<String> dirs = new HashSet<>();
//      @SuppressWarnings("deprecation")
//      final VirtualFile projectBase = project.getBaseDir();
//      if (projectBase != null) {
//        dirs.add(project.getBasePath());
//        ReadAction.run(() -> {
//          for (Module module : ModuleManager.getInstance(project).getModules()) {
//            for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
//              if (!VfsUtilCore.isAncestor(projectBase, root, false)) {
//                dirs.add(root.getPath());
//              }
//            }
//          }
//        });
//      }
//      dirs.add(PathManager.getConfigPath());
//      return new CachedValueProvider.Result<>(dirs, ProjectRootModificationTracker.getInstance(project));
//    });
//  }
//
//  // TODO shouldn't be needed?
//  private class CachedPairsProvider implements CachedValueProvider<List<OutPair>> {
//    private final @NotNull  String         myFilePath;
//    private final @NotNull  Set<String>    myRootDirs;
//    private final @Nullable ParserCallback myCallback;
//
//    private @Nullable Future<List<OutPair>> myFuture;
//    private long myParentModificationCount;
//
//    private CachedPairsProvider(@NotNull String filePath,
//                                @NotNull Set<String> rootDirs,
//                                @Nullable ParserCallback callback) {
//      myFilePath = filePath;
//      myRootDirs = rootDirs;
//      myCallback = callback;
//    }
//
//    @Override
//    public @Nullable Result<List<OutPair>> compute() {
//      try {
//        List<OutPair> outPairs = getProperties();
//        return CachedValueProvider.Result.create(outPairs, SettingsProviderComponentOld.this);
//      }
//      catch (TimeoutException timeoutException) {
//        LOG.warn("Timeout processing .editorconfig for " + myFilePath);
//        // Once there's a timeout, don't process the file again.
//        return CachedValueProvider.Result.create(
//          Collections.singletonList(new OutPair(ERROR, "Timeout")), ModificationTracker.NEVER_CHANGED);
//      }
//      catch (Exception error) {
//        ArrayList<OutPair> errorResult = new ArrayList<>();
//        errorResult.add(new OutPair(ERROR, error.getMessage()));
//        return CachedValueProvider.Result.create(errorResult, SettingsProviderComponentOld.this);
//      }
//    }
//
//    private synchronized List<OutPair> getProperties()
//      throws ExecutionException, InterruptedException, EditorConfigException, TimeoutException {
//      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
//        return editorConfig.getProperties(myFilePath, myRootDirs, myCallback);
//      }
//      long currParentModificationCount = SettingsProviderComponentOld.this.getModificationCount();
//      if (myFuture == null || myParentModificationCount != currParentModificationCount) {
//        myParentModificationCount = currParentModificationCount;
//        if (myFuture != null && !myFuture.isDone()) {
//          myFuture.cancel(true);
//        }
//        myFuture =
//          ApplicationManager.getApplication().executeOnPooledThread(() -> {
//            List<OutPair> pairs = new ArrayList<>();
//            try {
//              pairs.addAll(editorConfig.getProperties(myFilePath, myRootDirs, myCallback));
//            }
//            catch (ParsingException pe) { // .editorconfig may be temporarily incorrect
//              pairs.add(new OutPair(ERROR, pe.getMessage()));
//            }
//            return pairs;
//          });
//      }
//      return myFuture.get(TIMEOUT, TimeUnit.SECONDS);
//    }
//  }
//}
