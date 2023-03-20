// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PairProcessor;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GdslFileType;
import org.jetbrains.plugins.groovy.dsl.DslActivationStatus.Status;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.OriginAwareMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class GroovyDslFileIndex {
  private static final Key<Pair<GroovyDslExecutor, Long>> CACHED_EXECUTOR = Key.create("CachedGdslExecutor");
  private static final Key<CachedValue<List<GroovyDslScript>>> SCRIPTS_CACHE = Key.create("GdslScriptCache");
  private static final Logger LOG = Logger.getInstance(GroovyDslFileIndex.class);

  private static final MultiMap<String, LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>> filesInProcessing =
    MultiMap.createConcurrent();

  private static final ExecutorService ourPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("GroovyDSLIndex Pool", 4);

  private GroovyDslFileIndex() {}

  @Nullable
  @NlsSafe
  public static String getError(VirtualFile file) {
    DslActivationStatus.Entry info = DslActivationStatus.getInstance().getGdslFileInfo(file);
    return info == null ? null : info.error;
  }

  public static boolean isActivated(@NotNull VirtualFile file) {
    return getStatus(file) == Status.ACTIVE;
  }

  public static void activate(final VirtualFile vfile) {
    setStatusAndError(vfile, Status.ACTIVE, null);
    clearScriptCache();
  }

  public static Status getStatus(@NotNull VirtualFile file) {
    DslActivationStatus.Entry info = DslActivationStatus.getInstance().getGdslFileInfo(file);
    return info == null ? Status.ACTIVE : info.status;
  }

  private static void clearScriptCache() {
    Application app = ApplicationManager.getApplication();
    app.invokeLater(() -> {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        project.putUserData(SCRIPTS_CACHE, null);
        PsiManagerEx.getInstanceEx(project).dropPsiCaches();
      }
    }, app.getDisposed());
  }

  public static void disableFile(@NotNull VirtualFile vfile, @NotNull Status status, @NlsSafe @Nullable String error) {
    assert status != Status.ACTIVE;
    setStatusAndError(vfile, status, error);
    vfile.putUserData(CACHED_EXECUTOR, null);
    clearScriptCache();
  }

  private static void setStatusAndError(@NotNull VirtualFile vfile, @NotNull Status status, @NlsSafe @Nullable String error) {
    DslActivationStatus.Entry entry = DslActivationStatus.getInstance().getGdslFileInfoOrCreate(vfile);
    entry.status = status;
    entry.error = error;
  }

  @Nullable
  private static GroovyDslExecutor getCachedExecutor(@NotNull final VirtualFile file, final long stamp) {
    final Pair<GroovyDslExecutor, Long> pair = file.getUserData(CACHED_EXECUTOR);
    if (pair == null || pair.second.longValue() != stamp) {
      return null;
    }
    return pair.first;
  }

  @Nullable
  public static PsiClassType processScriptSuperClasses(@NotNull GroovyFile scriptFile) {
    if (!scriptFile.isScript()) return null;

    final VirtualFile virtualFile = scriptFile.getOriginalFile().getVirtualFile();
    if (virtualFile == null) return null;
    final String filePath = virtualFile.getPath();


    List<Trinity<String, String, GroovyDslScript>> supers = new ArrayList<>();
    final Project project = scriptFile.getProject();
    for (GroovyDslScript script : getDslScripts(project)) {
      final MultiMap staticInfo = script.getStaticInfo();
      //noinspection unchecked
      final Collection infos = staticInfo.get("scriptSuperClass");

      for (Object info : infos) {
        if (info instanceof @NonNls Map map) {

          final Object _pattern = map.get("pattern");
          final Object _superClass = map.get("superClass");

          if (_pattern instanceof String pattern && _superClass instanceof String superClass) {

            try {
              if (Pattern.matches(".*" + pattern, filePath)) {
                supers.add(Trinity.create(superClass, pattern, script));
              }
            }
            catch (RuntimeException e) {
              script.handleDslError(e);
            }
          }
        }
      }
    }

    if (!supers.isEmpty()) {
      final String className = supers.get(0).first;
      final GroovyDslScript script = supers.get(0).third;
      try {
        return TypesUtil.createTypeByFQClassName(className, scriptFile);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (RuntimeException e) {
        script.handleDslError(e);
        return null;
      }
    }
    /*else if (supers.size() > 1) {
      StringBuilder buffer = new StringBuilder("Several script super class patterns match file ").append(filePath).append(". <p> ");
      for (Trinity<String, String, GroovyDslScript> aSuper : supers) {
        buffer.append(aSuper.third.getFilePath()).append(" ").append(aSuper.second).append('\n');
      }
      NOTIFICATION_GROUP.createNotification("DSL script execution error", buffer.toString(), NotificationType.ERROR, null).notify(project);
      return null;
    }*/
    else {
      return null;
    }
  }

  public static boolean processExecutors(
    @NotNull PsiClassType psiType,
    @NotNull PsiElement place,
    @NotNull PairProcessor<? super CustomMembersHolder, ? super GroovyClassDescriptor> processor
  ) {
    if (insideAnnotation(place)) {
      // Basic filter, all DSL contexts are applicable for reference expressions only
      return true;
    }

    final PsiFile placeFile = place.getContainingFile().getOriginalFile();
    final PsiClass psiClass = psiType.resolve();
    if (psiClass == null) {
      return true;
    }

    for (GroovyDslScript script : getDslScripts(placeFile.getProject())) {
      GroovyClassDescriptor descriptor = new GroovyClassDescriptor(psiType, psiClass, place, placeFile);
      CustomMembersHolder holder = script.processExecutor(descriptor);
      VirtualFile origin = script.getFile();
      if (origin != null) {
        holder = new OriginAwareMembersHolder(origin, holder);
      }
      if (!processor.process(holder, descriptor)) {
        return false;
      }
    }
    return true;
  }

  private static boolean insideAnnotation(@Nullable PsiElement place) {
    while (place != null) {
      if (place instanceof PsiAnnotation) return true;
      if (place instanceof GrClosableBlock ||
          place instanceof GrTypeDefinition ||
          place instanceof PsiFile) {
        return false;
      }
      place = place.getParent();
    }
    return false;
  }

  private static List<VirtualFile> getGdslFiles(final Project project) {
    final List<VirtualFile> result = new ArrayList<>(bundledGdslFiles.getValue());
    if (TrustedProjects.isTrusted(project)) {
      result.addAll(getProjectGdslFiles(project));
    }
    return result;
  }

  private static final ClearableLazyValue<List<VirtualFile>> bundledGdslFiles = ClearableLazyValue.create(() -> {
    final List<VirtualFile> result = new ArrayList<>();
    for (File file : getBundledScriptFolders()) {
      if (file.exists()) {
        File[] children = file.listFiles();
        if (children != null) {
          for (File child : children) {
            final String fileName = child.getName();
            if (fileName.endsWith(".gdsl")) {
              String path = FileUtil.toSystemIndependentName(child.getPath());
              String url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, path);
              ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
            }
          }
        }
      }
    }
    return result;
  });

  static {
    GdslScriptProvider.EP_NAME.addChangeListener(() -> {
      bundledGdslFiles.drop();
    }, null);
  }

  static List<VirtualFile> getProjectGdslFiles(Project project) {
    final List<VirtualFile> result = new ArrayList<>();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    for (VirtualFile vfile : FileTypeIndex.getFiles(GdslFileType.INSTANCE, scope)) {
      if (FileTypeRegistry.getInstance().getFileTypeByFileName(vfile.getNameSequence()) != GdslFileType.INSTANCE) {
        continue;
      }
      if (!vfile.isValid()) {
        continue;
      }
      if (fileIndex.isInLibrarySource(vfile)) {
        continue;
      }
      if (!fileIndex.isInLibraryClasses(vfile)) {
        if (!fileIndex.isInSourceContent(vfile) || !isActivated(vfile)) {
          continue;
        }
      }

      result.add(vfile);
    }
    return result;
  }


  @NotNull
  private static Set<File> getBundledScriptFolders() {
    final GdslScriptProvider[] extensions = GdslScriptProvider.EP_NAME.getExtensions();
    final Set<Class<?>> classes = new HashSet<>(ContainerUtil.map(extensions, GdslScriptProvider::getClass));
    classes.add(GdslScriptProvider.class); // for default extension

    Set<File> scriptFolders = new LinkedHashSet<>();
    for (Class<?> aClass : classes) {
      File jarPath = new File(PathUtil.getJarPathForClass(aClass));
      if (jarPath.isFile()) {
        jarPath = jarPath.getParentFile();
      }
      scriptFolders.add(new File(jarPath, "standardDsls"));
    }
    return scriptFolders;
  }

  private static List<GroovyDslScript> getDslScripts(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, SCRIPTS_CACHE, () -> {
      if (GdslUtil.ourGdslStopped) {
        return CachedValueProvider.Result.create(Collections.emptyList(), ModificationTracker.NEVER_CHANGED);
      }

      // eagerly initialize some services used by background gdsl parsing threads
      // because service init requires a read action
      // and there could be a deadlock with a write action waiting already on EDT
      // if current thread is inside a non-cancellable read action
      GdslScriptBase.getIdeaVersion();
      DslActivationStatus.getInstance();

      int count = 0;

      List<GroovyDslScript> result = new ArrayList<>();

      final LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue =
        new LinkedBlockingQueue<>();

      for (VirtualFile vfile : getGdslFiles(project)) {
        final long stamp = vfile.getModificationStamp();
        final GroovyDslExecutor cached = getCachedExecutor(vfile, stamp);
        if (cached == null) {
          scheduleParsing(queue, project, vfile, stamp, LoadTextUtil.loadText(vfile).toString());
          count++;
        }
        else {
          result.add(new GroovyDslScript(project, vfile, cached, vfile.getPath()));
        }
      }

      try {
        while (count > 0 && !GdslUtil.ourGdslStopped) {
          ProgressManager.checkCanceled();
          final Pair<VirtualFile, GroovyDslExecutor> pair = queue.poll(20, TimeUnit.MILLISECONDS);
          if (pair != null) {
            count--;
            if (pair.second != null) {
              result.add(new GroovyDslScript(project, pair.first, pair.second, pair.first.getPath()));
            }
          }
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }

      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
    }, false);
  }

  private static void scheduleParsing(final LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue,
                                      final Project project,
                                      final VirtualFile vfile,
                                      final long stamp,
                                      final String text) {
    final String fileUrl = vfile.getUrl();

    final Runnable parseScript = () -> {
      GroovyDslExecutor executor = getCachedExecutor(vfile, stamp);
      try {
        if (executor == null && isActivated(vfile)) {
          executor = createExecutor(text, vfile, project);
          // executor is not only time-consuming to create, but also takes some PermGenSpace
          // => we can't afford garbage-collecting it together with PsiFile
          // => cache globally by file instance
          vfile.putUserData(CACHED_EXECUTOR, Pair.create(executor, stamp));
          if (executor != null) {
            setStatusAndError(vfile, Status.ACTIVE, null);
          }
        }
      }
      finally {
        // access to our MultiMap should be synchronized
        synchronized (filesInProcessing) {
          // put evaluated executor to all queues
          for (LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue1 : filesInProcessing.remove(fileUrl)) {
            queue1.offer(Pair.create(vfile, executor));
          }
        }
      }
    };

    synchronized (filesInProcessing) { //ensure that only one thread calculates dsl executor
      final boolean isNewRequest = !filesInProcessing.containsKey(fileUrl);
      filesInProcessing.putValue(fileUrl, queue);
      if (isNewRequest) {
        ourPool.execute(parseScript);
      }
    }
  }

  @Nullable
  private static GroovyDslExecutor createExecutor(String text, VirtualFile vfile, final Project project) {
    if (GdslUtil.ourGdslStopped) {
      return null;
    }

    try {
      return GroovyDslExecutor.createAndRunExecutor(text, vfile.getName());
    }
    catch (final Throwable e) {
      if (project.isDisposed()) {
        LOG.error(e);
        return null;
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(e);
        return null;
      }
      DslErrorReporter.getInstance().invokeDslErrorPopup(e, project, vfile);

      //noinspection InstanceofCatchParameter
      if (e instanceof OutOfMemoryError) {
        GdslUtil.stopGdsl();
        throw (Error)e;
      }
      //noinspection InstanceofCatchParameter
      if (e instanceof NoClassDefFoundError) {
        GdslUtil.stopGdsl();
        throw (NoClassDefFoundError)e;
      }

      return null;
    }
  }

  public static class MyFileListener implements BulkFileListener {
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
      for (VFileEvent event : events) {
        if (event instanceof VFileContentChangeEvent && !event.isFromRefresh()) {
          VirtualFile file = event.getFile();
          if (!GdslUtil.GDSL_FILTER.value(file) || getStatus(file) != Status.ACTIVE) {
            continue;
          }

          disableFile(file, Status.MODIFIED, null);
        }
      }
    }
  }
}
