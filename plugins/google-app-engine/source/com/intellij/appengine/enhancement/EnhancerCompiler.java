package com.intellij.appengine.enhancement;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
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

  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    final Module[] affectedModules = context.getCompileScope().getAffectedModules();
    for (Module module : affectedModules) {
      for (AppEngineFacet facet : FacetManager.getInstance(module).getFacetsByType(AppEngineFacet.ID)) {
        if (facet.getConfiguration().isRunEnhancerOnMake()) {
          final VirtualFile outputRoot = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
          if (outputRoot != null) {
            collectItems(outputRoot, facet, context, items);
          }
        }
      }
    }
    return items.toArray(new ProcessingItem[items.size()]);
  }

  private static void collectItems(@NotNull VirtualFile file, AppEngineFacet facet, CompileContext context, List<ProcessingItem> items) {
    if (file.isDirectory()) {
      final VirtualFile[] files = file.getChildren();
      for (VirtualFile child : files) {
        collectItems(child, facet, context, items);
      }
    }
    else if (StdFileTypes.CLASS.equals(file.getFileType())) {
      final VirtualFile sourceFile = ((CompileContextEx)context).getSourceFileByOutputFile(file);
      if (sourceFile != null && facet.shouldRunEnhancerFor(sourceFile)) {
        items.add(new ClassFileItem(file, facet));
      }
    }
  }

  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    context.getProgressIndicator().setText("Enhancing classes...");
    MultiValuesMap<AppEngineFacet, ClassFileItem> itemsByFacet = new MultiValuesMap<AppEngineFacet, ClassFileItem>();
    for (ProcessingItem item : items) {
      final ClassFileItem classFileItem = (ClassFileItem)item;
      itemsByFacet.put(classFileItem.getFacet(), classFileItem);
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
        throw new CantRunException("Valid App Engine SDK isn't specified for '" + facet.getName() + "' facet (module '" + facet.getModule() + "')");
      }

      final JavaParameters javaParameters = new JavaParameters();
      new ReadAction() {
        protected void run(final Result result) throws Throwable {
          context.getProgressIndicator().setText2("'" + facet.getModule().getName() + "' module, '" + facet.getWebFacet().getName() + "' facet, processing " + items.size() + " classes...");
          javaParameters.configureByModule(facet.getModule(), JavaParameters.JDK_AND_CLASSES);
          javaParameters.getClassPath().addFirst(sdk.getToolsApiJarFile().getAbsolutePath());

          final ParametersList vmParameters = javaParameters.getVMParametersList();
          vmParameters.add("-Xmx256m");

          final ParametersList programParameters = javaParameters.getProgramParametersList();
          programParameters.add("-v");
          for (ClassFileItem item : items) {
            programParameters.add(FileUtil.toSystemDependentName(item.getFile().getPath()));
          }

          javaParameters.setMainClass("com.google.appengine.tools.enhancer.Enhance");
        }
      }.execute().throwException();


      final GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters);
      if (LOG.isDebugEnabled()) {
        LOG.debug("starting enchancer: " + commandLine.getCommandLineString());
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

  public ValidityState createValidityState(DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  @NotNull
  public String getDescription() {
    return "Google App Engine Enhancer";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }
}
