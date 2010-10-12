package org.jetbrains.javafx.build;

import com.intellij.compiler.CompilerException;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.JavaFxFileType;
import org.jetbrains.javafx.facet.JavaFxFacet;
import org.jetbrains.javafx.sdk.JavaFxSdkType;
import org.jetbrains.javafx.sdk.JavaFxSdkUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.javafx.build.JavaFxCompiler");

  @Override
  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return file.getFileType() instanceof JavaFxFileType;
  }

  @Override
  public void compile(CompileContext context, Chunk<Module> moduleChunk, VirtualFile[] files, OutputSink sink) {
    Map<Module, List<VirtualFile>> mapModulesToVirtualFiles;
    if (moduleChunk.getNodes().size() == 1) {
      mapModulesToVirtualFiles = Collections.singletonMap(moduleChunk.getNodes().iterator().next(), Arrays.asList(files));
    }
    else {
      mapModulesToVirtualFiles = CompilerUtil.buildModuleToFilesMap(context, files);
    }
    for (final Module module : moduleChunk.getNodes()) {
      final List<VirtualFile> moduleFiles = mapModulesToVirtualFiles.get(module);
      if (moduleFiles == null) {
        continue;
      }
      final JavaFxFacet facet = FacetManager.getInstance(module).getFacetByType(JavaFxFacet.ID);
      if (facet == null) {
        continue;
      }
      final Sdk sdk = JavaFxSdkUtil.getSdk(facet);
      final JavaFxCompilerHandler compilerHandler = new JavaFxCompilerHandler(context, sdk, module.getProject());
      context.getProgressIndicator().checkCanceled();
      try {
        compilerHandler.runCompiler(context, module, moduleFiles);
      }
      catch (CompilerException e) {
        LOG.debug(e);
        context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      }
      finally {
        compilerHandler.compileFinished();
      }
      sink.add(compilerHandler.getCompilerOutputPath(), compilerHandler.getOutputItems(), VirtualFile.EMPTY_ARRAY);
    }
  }

  @NotNull
  @Override
  public String getDescription() {
    return "JavaFxCompiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    final Module[] modules = scope.getAffectedModules();
    for (final Module module : modules) {
      final JavaFxFacet facet = FacetManager.getInstance(module).getFacetByType(JavaFxFacet.ID);
      if (facet == null) {
        continue;
      }
      final Sdk sdk = JavaFxSdkUtil.getSdk(facet);
      if (sdk == null || !(sdk.getSdkType() instanceof JavaFxSdkType)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(module.getProject(),
                                     JavaFxBundle.message("javafx.sdk.is.not.set.facet.$1.module.$2", facet.getName(), module.getName()),
                                     JavaFxBundle.message("javafx.compiler.problem"));
          }
        });
        return false;
      }
    }
    return true;
  }
}
