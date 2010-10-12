package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxClassNameIndex;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxResolveUtil {
  public static ResolveResult[] findClass(final String name, final Project project) {
    final Collection<JavaFxClassDefinition> javaFxClassDefinitions =
      StubIndex.getInstance().get(JavaFxClassNameIndex.KEY, name, project, ProjectScope.getAllScope(project));
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final ArrayList<JavaFxClassDefinition> result = new ArrayList<JavaFxClassDefinition>();
    for (JavaFxClassDefinition classDefinition : javaFxClassDefinitions) {
      final VirtualFile file = classDefinition.getContainingFile().getVirtualFile();
      if (file != null && fileIndex.isInContent(file)) {
        result.add(classDefinition);
      }
    }
    return PsiElementResolveResult.createResults(result);
  }

  public static boolean treeWalkUp(final PsiElement element, final PsiScopeProcessor processor) {
    PsiElement lastParent = null;
    PsiElement run = element;
    final ResolveState state = ResolveState.initial();
    while (run != null) {
      ProgressManager.checkCanceled();
      if (!run.processDeclarations(processor, state, lastParent, element)) {
        return false;
      }
      lastParent = run;
      run = run.getContext();
    }
    return true;
  }

  public static boolean processElements(final JavaFxElement[] elements,
                                        final PsiElement lastParent,
                                        final PsiScopeProcessor processor,
                                        final ResolveState state) {
    for (JavaFxElement element : elements) {
      if (element != lastParent && !processor.execute(element, state)) {
        return false;
      }
    }
    return true;
  }

  public static ResolveResult[] createResolveResult(@Nullable final PsiElement result) {
    if (result == null) {
      return PsiElementResolveResult.EMPTY_ARRAY;
    }
    return new PsiElementResolveResult[]{new PsiElementResolveResult(result)};
  }

  private JavaFxResolveUtil() {
  }
}
