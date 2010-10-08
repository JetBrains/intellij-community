package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.rt.EnhancerRunner;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.Cache;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.generic.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class EnhancerCompilerInstance extends GenericCompilerInstance<EnhancementTarget, ClassFileItem, String, VirtualFileWithDependenciesState, DummyPersistentState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.enhancement.EnhancerCompilerInstance");
  private Project myProject;

  public EnhancerCompilerInstance(CompileContext context) {
    super(context);
    myProject = context.getProject();
  }

  @NotNull
  @Override
  public List<EnhancementTarget> getAllTargets() {
    List<EnhancementTarget> targets = new ArrayList<EnhancementTarget>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (AppEngineFacet facet : FacetManager.getInstance(module).getFacetsByType(AppEngineFacet.ID)) {
        if (facet.getConfiguration().isRunEnhancerOnMake()) {
          final CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(module);
          if (moduleExtension != null) {
            final VirtualFile outputRoot = moduleExtension.getCompilerOutputPath();
            if (outputRoot != null) {
              targets.add(new EnhancementTarget(facet, outputRoot));
            }
          }
        }
      }
    }
    return targets;
  }

  @NotNull
  @Override
  public List<EnhancementTarget> getSelectedTargets() {
    return getAllTargets();
  }

  @Override
  public void processObsoleteTarget(@NotNull String targetId,
                                    @NotNull List<GenericCompilerCacheState<String, VirtualFileWithDependenciesState, DummyPersistentState>> obsoleteItems) {
  }

  @NotNull
  @Override
  public List<ClassFileItem> getItems(@NotNull EnhancementTarget target) {
    List<ClassFileItem> items = new ArrayList<ClassFileItem>();
    try {
      final ClassFilesCollector classFilesCollector = new ClassFilesCollector((CompileContextEx)myContext, items, target.getFacet());
      classFilesCollector.collectItems(target.getOutputRoot(), "");
    }
    catch (CacheCorruptedException e) {
      myContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      LOG.info(e);
    }
    return items;
  }

  @Override
  public void processItems(@NotNull final EnhancementTarget target,
                           @NotNull final List<GenericCompilerProcessingItem<ClassFileItem, VirtualFileWithDependenciesState, DummyPersistentState>> changedItems,
                           @NotNull List<GenericCompilerCacheState<String, VirtualFileWithDependenciesState, DummyPersistentState>> obsoleteItems,
                           @NotNull final OutputConsumer<ClassFileItem> consumer) {
    CompilerUtil.runInContext(myContext, "Enhancing classes...", new ThrowableRunnable<RuntimeException>() {
      @Override
      public void run() {
        List<ClassFileItem> toEnhance = new ArrayList<ClassFileItem>();
        for (GenericCompilerProcessingItem<ClassFileItem, VirtualFileWithDependenciesState, DummyPersistentState> item : changedItems) {
          final ClassFileItem classFileItem = item.getItem();
          if (myContext.getCompileScope().belongs(classFileItem.getSourceFile().getUrl())) {
            toEnhance.add(classFileItem);
          }
        }

        if (!toEnhance.isEmpty()) {
          if (runEnhancer(target.getFacet(), toEnhance)) {
            for (ClassFileItem item : toEnhance) {
              consumer.addProcessedItem(item);
            }
          }
        }
      }
    });

  }

  private boolean runEnhancer(final AppEngineFacet facet, final Collection<ClassFileItem> items) {
    try {
      final AppEngineSdk sdk = facet.getSdk();
      if (!sdk.isValid()) {
        throw new CantRunException("Valid App Engine SDK isn't specified for '" + facet.getName() + "' facet (module '" + facet.getModule().getName() + "')");
      }

      final JavaParameters javaParameters = new JavaParameters();
      new ReadAction() {
        protected void run(final Result result) throws Throwable {
          myContext.getProgressIndicator().setText2("'" + facet.getModule().getName() + "' module, '" + facet.getWebFacet().getName() + "' facet, processing " + items.size() + " classes...");
          javaParameters.configureByModule(facet.getModule(), JavaParameters.JDK_AND_CLASSES);

          final PathsList classPath = javaParameters.getClassPath();
          classPath.addFirst(sdk.getToolsApiJarFile().getAbsolutePath());
          removeAsmJarFromClasspath(classPath);

          final ParametersList vmParameters = javaParameters.getVMParametersList();
          vmParameters.add("-Xmx256m");

          javaParameters.setMainClass(EnhancerRunner.class.getName());
          classPath.addFirst(PathUtil.getJarPathForClass(EnhancerRunner.class));

          final File argsFile = FileUtil.createTempFile("appEngineEnhanceFiles", ".txt");
          PrintWriter writer = new PrintWriter(argsFile);
          try {
            for (ClassFileItem item : items) {
              writer.println(FileUtil.toSystemDependentName(item.getFile().getPath()));
            }
          }
          finally {
            writer.close();
          }

          final ParametersList programParameters = javaParameters.getProgramParametersList();
          programParameters.add(argsFile.getAbsolutePath());
          programParameters.add("com.google.appengine.tools.enhancer.Enhance");
          programParameters.add("-api");
          programParameters.add(facet.getConfiguration().getPersistenceApi().getName());
          programParameters.add("-v");

        }
      }.execute().throwException();


      final GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters);
      if (LOG.isDebugEnabled()) {
        LOG.debug("starting enhancer: " + commandLine.getCommandLineString());
      }
      final Process process = commandLine.createProcess();
      EnhancerProcessHandler handler = new EnhancerProcessHandler(process, commandLine.getCommandLineString(), myContext);
      handler.startNotify();
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(ProcessEvent event) {
          final int exitCode = event.getExitCode();
          if (exitCode != 0) {
            myContext.addMessage(CompilerMessageCategory.ERROR, "Enhancement process terminated with exit code " + exitCode, null, -1, -1);
          }
        }
      });
      handler.waitFor();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      myContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      LOG.info(e);
    }
    return myContext.getMessageCount(CompilerMessageCategory.ERROR) == 0;
  }

  private static void removeAsmJarFromClasspath(PathsList classPath) {
    List<String> toRemove = new ArrayList<String>();
    for (String filePath : classPath.getPathList()) {
      if (filePath.endsWith(".jar")) {
        final VirtualFile root =
          JarFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(filePath) + JarFileSystem.JAR_SEPARATOR);
        if (root != null && LibraryUtil.isClassAvailableInLibrary(new VirtualFile[]{root}, "org.objectweb.asm.ClassReader")) {
          toRemove.add(filePath);
        }
      }
    }
    for (String path : toRemove) {
      classPath.remove(path);
    }
  }

  private static class ClassFilesCollector {
    private CompileContextEx myContext;
    private List<ClassFileItem> myItems;
    private AppEngineFacet myFacet;
    private final SymbolTable mySymbolTable;
    private final Cache myCache;
    private final LocalFileSystem myLocalFileSystem;

    public ClassFilesCollector(CompileContextEx context,
                               List<ClassFileItem> items,
                               AppEngineFacet facet) throws CacheCorruptedException {
      myContext = context;
      myItems = items;
      myFacet = facet;
      mySymbolTable = myContext.getDependencyCache().getSymbolTable();
      myCache = myContext.getDependencyCache().getCache();
      myLocalFileSystem = LocalFileSystem.getInstance();
    }

    public void collectItems(@NotNull VirtualFile file, String fullName) throws CacheCorruptedException {
      if (file.isDirectory()) {
        final VirtualFile[] files = file.getChildren();
        for (VirtualFile child : files) {
          collectItems(child, StringUtil.getQualifiedName(fullName, child.getName()));
        }
        return;
      }

      if (StdFileTypes.CLASS.equals(file.getFileType())) {
        final VirtualFile sourceFile = myContext.getSourceFileByOutputFile(file);
        if (sourceFile != null && myFacet.shouldRunEnhancerFor(sourceFile)) {
          String className = StringUtil.trimEnd(fullName, ".class");
          int classId = mySymbolTable.getId(className);
          List<VirtualFile> dependencies = new SmartList<VirtualFile>();
          while (classId != Cache.UNKNOWN) {
            final String path = myCache.getPath(classId);
            if (!StringUtil.isEmpty(path)) {
              final VirtualFile classFile = myLocalFileSystem.findFileByPath(FileUtil.toSystemIndependentName(path));
              if (classFile != null) {
                dependencies.add(classFile);
              }
            }
            classId = myCache.getSuperQualifiedName(classId);
          }
          myItems.add(new ClassFileItem(file, sourceFile, dependencies));
        }
      }
    }
  }
}
