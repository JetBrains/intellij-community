// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;

import java.util.List;

/**
 * @author peter
 */
public class GroovyDslScript {
  private static final Logger LOG = Logger.getInstance(GroovyDslScript.class);
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

  public @NotNull CustomMembersHolder processExecutor(@NotNull GroovyClassDescriptor descriptor) {
    CustomMembersHolder holder = myFactorTree.retrieve(descriptor);
    try {
      if (holder == null) {
        holder = addGdslMembers(descriptor);
        myFactorTree.cache(descriptor, holder);
      }
      return holder;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      handleDslError(e);
      return CustomMembersHolder.EMPTY;
    }
  }

  private CustomMembersHolder addGdslMembers(@NotNull GroovyClassDescriptor descriptor) {
    final ProcessingContext ctx = new ProcessingContext();
    ctx.put(GdslUtil.INITIAL_CONTEXT, descriptor);
    try {
      if (!isApplicable(executor, descriptor, ctx)) {
        return CustomMembersHolder.EMPTY;
      }

      return executor.processVariants(descriptor, ctx);
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

  public @Nullable VirtualFile getFile() {
    return file;
  }

  @Override
  @NonNls
  public String toString() {
    return "GroovyDslScript: " + myPath;
  }

  @NotNull
  public MultiMap getStaticInfo() {
    return executor.getStaticInfo();
  }
}
