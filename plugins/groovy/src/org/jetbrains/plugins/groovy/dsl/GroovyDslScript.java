package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author peter
 */
public class GroovyDslScript {
  public static final Key<GroovyClassDescriptor> INITIAL_CONTEXT = Key.create("gdsl.initialContext");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslScript");
  private final Project project;
  @Nullable private final VirtualFile file;
  private final GroovyDslExecutor executor;
  private final String myPath;
  private final CachedValue<FactorTree> myMaps;

  public GroovyDslScript(final Project project, @Nullable VirtualFile file, GroovyDslExecutor executor, String path) {
    this.project = project;
    this.file = file;
    this.executor = executor;
    myPath = path;
    myMaps = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<FactorTree>() {
      @Override
      public Result<FactorTree> compute() {
        return Result.create(new FactorTree(), PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
      }
    }, false);
  }


  public boolean processExecutor(PsiScopeProcessor processor,
                                 final PsiType psiType,
                                 final GroovyPsiElement place,
                                 final PsiFile placeFile,
                                 final String qname,
                                 ResolveState state) {
    final FactorTree cache = myMaps.getValue();
    CustomMembersHolder holder = cache.retrieve(place, placeFile, qname);
    GroovyClassDescriptor descriptor = new GroovyClassDescriptor(psiType, place, placeFile);
    try {
      if (holder == null) {
        holder = addGdslMembers(descriptor, qname, psiType);
        cache.cache(descriptor, holder);
      }

      return holder.processMembers(descriptor, processor, state);
    }
    catch (IncorrectOperationException e) {
      LOG.error("Error while processing dsl script '" + myPath + "'", e);
      return false;
    }
  }

  private CustomMembersHolder addGdslMembers(GroovyClassDescriptor descriptor, String qname, final PsiType psiType) {
    final ProcessingContext ctx = new ProcessingContext();
    ctx.put(ClassContextFilter.getClassKey(qname), psiType);
    ctx.put(INITIAL_CONTEXT, descriptor);
    try {
      if (!isApplicable(executor, descriptor, ctx)) {
        return CustomMembersHolder.EMPTY;
      }

      final ExtensibleCustomMembersGenerator generator = new ExtensibleCustomMembersGenerator(descriptor, psiType);
      executor.processVariants(descriptor, generator, ctx);
      return generator.getMembersHolder();
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
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (OutOfMemoryError e) {
      throw e;
    }
    catch (Throwable e) { // To handle exceptions in definition script
      handleDslError(e);
    }
    return CustomMembersHolder.EMPTY;
  }

  private static boolean isApplicable(GroovyDslExecutor executor, GroovyClassDescriptor descriptor, final ProcessingContext ctx) {
    for (Pair<ContextFilter, Closure> pair : executor.getEnhancers()) {
      if (pair.first.isApplicable(descriptor, ctx)) {
        return true;
      }
    }
    return false;
  }

  private boolean handleDslError(Throwable e) {
    LOG.error(e);
    if (project.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }
    if (file != null) {
      GroovyDslFileIndex.invokeDslErrorPopup(e, project, file);
    }
    return false;
  }

  @Override
  public String toString() {
    return "GroovyDslScript: " + myPath;
  }
}
