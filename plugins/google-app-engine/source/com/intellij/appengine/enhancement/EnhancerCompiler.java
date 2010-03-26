package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.make.Cache;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class EnhancerCompiler implements ClassPostProcessingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.enhancement.EnhancerCompiler");
  private Project myProject;

  public EnhancerCompiler(Project project) {
    myProject = project;
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (AppEngineFacet facet : FacetManager.getInstance(module).getFacetsByType(AppEngineFacet.ID)) {
        if (facet.getConfiguration().isRunEnhancerOnMake()) {
          final VirtualFile outputRoot = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
          if (outputRoot != null) {
            try {
              final ClassFilesCollector classFilesCollector = new ClassFilesCollector((CompileContextEx)context, items, facet, outputRoot);
              classFilesCollector.collectItems(outputRoot, "");
            }
            catch (CacheCorruptedException e) {
              context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
              LOG.info(e);
              break;
            }
          }
        }
      }
    }
    return items.toArray(new ProcessingItem[items.size()]);
  }

  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    context.getProgressIndicator().setText("Enhancing classes...");
    MultiValuesMap<AppEngineFacet, ClassFileItem> itemsByFacet = new MultiValuesMap<AppEngineFacet, ClassFileItem>();
    for (ProcessingItem item : items) {
      final ClassFileItem classFileItem = (ClassFileItem)item;
      if (context.getCompileScope().belongs(classFileItem.getSourceFile().getUrl())) {
        itemsByFacet.put(classFileItem.getFacet(), classFileItem);
      }
    }

    List<ProcessingItem> processed = new ArrayList<ProcessingItem>();
    for (AppEngineFacet facet : itemsByFacet.keySet()) {
      final Collection<ClassFileItem> classFileItems = itemsByFacet.get(facet);
      if (!runEnhancer(facet, classFileItems, context)) {
        break;
      }
      processed.addAll(classFileItems);
    }
    context.getProgressIndicator().setText("");
    context.getProgressIndicator().setText2("");
    return processed.toArray(new ProcessingItem[processed.size()]);
  }

  private static boolean runEnhancer(final AppEngineFacet facet, final Collection<ClassFileItem> items, final CompileContext context) {
    try {
      final AppEngineSdk sdk = facet.getSdk();
      if (!sdk.isValid()) {
        throw new CantRunException("Valid App Engine SDK isn't specified for '" + facet.getName() + "' facet (module '" + facet.getModule().getName() + "')");
      }

      final JavaParameters javaParameters = new JavaParameters();
      new ReadAction() {
        protected void run(final Result result) throws Throwable {
          final JavaParameters tempJavaParameters = new JavaParameters();
          context.getProgressIndicator().setText2("'" + facet.getModule().getName() + "' module, '" + facet.getWebFacet().getName() + "' facet, processing " + items.size() + " classes...");
          tempJavaParameters.configureByModule(facet.getModule(), JavaParameters.JDK_AND_CLASSES);
          final PathsList classPath = tempJavaParameters.getClassPath();
          classPath.addFirst(sdk.getToolsApiJarFile().getAbsolutePath());
          clearClasspath(classPath, javaParameters.getClassPath());

          final ParametersList vmParameters = javaParameters.getVMParametersList();
          vmParameters.add("-Xmx256m");

          final ParametersList programParameters = javaParameters.getProgramParametersList();
          programParameters.add("-api");
          programParameters.add(facet.getConfiguration().getPersistenceApi().getName());
          programParameters.add("-v");
          for (ClassFileItem item : items) {
            programParameters.add(FileUtil.toSystemDependentName(item.getFile().getPath()));
          }

          javaParameters.setMainClass("com.google.appengine.tools.enhancer.Enhance");

          javaParameters.setCharset(tempJavaParameters.getCharset());
          javaParameters.setEnv(tempJavaParameters.getEnv());
          javaParameters.setJdk(tempJavaParameters.getJdk());
        }
      }.execute().throwException();


      final GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters);
      if (LOG.isDebugEnabled()) {
        LOG.debug("starting enhancer: " + commandLine.getCommandLineString());
      }
      final Process process = commandLine.createProcess();
      EnhancerProcessHandler handler = new EnhancerProcessHandler(process, commandLine.getCommandLineString(), context);
      handler.startNotify();
      handler.waitFor();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      LOG.info(e);
    }
    return context.getMessageCount(CompilerMessageCategory.ERROR) == 0;
  }

  private static void clearClasspath(PathsList classPath, PathsList clearedClasspath) {
    for (String filePath : classPath.getPathList()) {
      if (filePath.endsWith(".jar")) {
        final VirtualFile root =
          JarFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(filePath) + JarFileSystem.JAR_SEPARATOR);
        if (root != null && LibraryUtil.isClassAvailableInLibrary(new VirtualFile[]{root}, "org.objectweb.asm.ClassReader")) {
          continue;
        }
      }
      clearedClasspath.add(filePath);
    }
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return ClassFileItemDependenciesState.load(in);
  }

  @NotNull
  public String getDescription() {
    return "Google App Engine Enhancer #2";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  private static class ClassFilesCollector {
    private CompileContextEx myContext;
    private List<ProcessingItem> myItems;
    private AppEngineFacet myFacet;
    private VirtualFile myOutputRoot;
    private final SymbolTable mySymbolTable;
    private final Cache myCache;
    private final LocalFileSystem myLocalFileSystem;

    public ClassFilesCollector(CompileContextEx context, List<ProcessingItem> items, AppEngineFacet facet, VirtualFile outputRoot) throws CacheCorruptedException {
      myContext = context;
      myItems = items;
      myFacet = facet;
      myOutputRoot = outputRoot;
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
          myItems.add(new ClassFileItem(file, sourceFile, myFacet, dependencies));
        }
      }
    }
  }
}
