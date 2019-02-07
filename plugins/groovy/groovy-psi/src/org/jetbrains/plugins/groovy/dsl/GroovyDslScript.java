// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;

import java.util.List;

/**
 * @author peter
 */
public class GroovyDslScript {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslScript");
  private final Project project;
  @Nullable private final VirtualFile file;
  private final GroovyDslExecutor executor;
  private final String myPath;
  private final FactorTree myFactorTree;

  public GroovyDslScript(final Project project, @Nullable VirtualFile file, @NotNull GroovyDslExecutor executor, String path) {
    this.project = project;
    this.file = file;
    this.executor = executor;
    myPath = path;
    myFactorTree = new FactorTree(project, executor);
  }

  public boolean processExecutor(final PsiType psiType,
                                 final PsiElement place,
                                 final PsiFile placeFile,
                                 NotNullLazyValue<String> typeText,
                                 PairProcessor<? super CustomMembersHolder, ? super GroovyClassDescriptor> processor) {
    CustomMembersHolder holder = myFactorTree.retrieve(place, placeFile, typeText);
    GroovyClassDescriptor descriptor = new GroovyClassDescriptor(psiType, place, placeFile);
    try {
      if (holder == null) {
        holder = addGdslMembers(descriptor, psiType);
        myFactorTree.cache(descriptor, holder);
      }

      return processor.process(holder, descriptor);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      handleDslError(e);
      return true;
    }
  }

  private CustomMembersHolder addGdslMembers(GroovyClassDescriptor descriptor, final PsiType psiType) {
    final ProcessingContext ctx = new ProcessingContext();
    ctx.put(GdslUtil.INITIAL_CONTEXT, descriptor);
    try {
      if (!isApplicable(executor, descriptor, ctx)) {
        return CustomMembersHolder.EMPTY;
      }

      return executor.processVariants(descriptor, ctx, psiType);
    }
    catch (InvokerInvocationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      if (cause instanceof OutOfMemoryError) {
        throw (OutOfMemoryError)cause;
      }
      handleDslError(e);
    }
    catch (ProcessCanceledException | OutOfMemoryError e) {
      throw e;
    }
    catch (Throwable e) { // To handle exceptions in definition script
      handleDslError(e);
    }
    return CustomMembersHolder.EMPTY;
  }

  private static boolean isApplicable(@NotNull GroovyDslExecutor executor, GroovyClassDescriptor descriptor, final ProcessingContext ctx) {
    List<Pair<ContextFilter,Closure>> enhancers = executor.getEnhancers();
    if (enhancers == null) {
      LOG.error("null enhancers");
      return false;
    }
    for (Pair<ContextFilter, Closure> pair : enhancers) {
      if (pair.first.isApplicable(descriptor, ctx)) {
        return true;
      }
    }
    return false;
  }

  public void handleDslError(Throwable e) {
    if (project.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(e);
    }
    if (file != null) {
      DslErrorReporter.getInstance().invokeDslErrorPopup(e, project, file);
    }
    else {
      LOG.info("Error when executing internal GDSL " + myPath, e);
      GdslUtil.stopGdsl();
    }
  }

  @Override
  public String toString() {
    return "GroovyDslScript: " + myPath;
  }

  @NotNull
  public MultiMap getStaticInfo() {
    return executor.getStaticInfo();
  }
}
