package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxPsiManager;
import org.jetbrains.javafx.lang.psi.JavaFxQualifiedNamedElement;
import org.jetbrains.javafx.lang.psi.impl.stubs.index.JavaFxQualifiedNameIndex;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPsiManagerImpl extends AbstractProjectComponent implements JavaFxPsiManager {
  public static JavaFxPsiManager getInstance(final Project project) {
    return project.getComponent(JavaFxPsiManager.class);
  }

  private final ConcurrentHashMap<String, PsiElement> myElementsCache = new ConcurrentHashMap<String, PsiElement>();
  private final JavaPsiFacade myJavaPsiFacade;

  protected JavaFxPsiManagerImpl(Project project) {
    super(project);
    myJavaPsiFacade = JavaPsiFacade.getInstance(myProject);
  }

  @Nullable
  @Override
  public PsiElement getElementByQualifiedName(final String qualifiedName) {
    final PsiElement psiElement = myElementsCache.get(qualifiedName);
    if (psiElement != null) {
      return psiElement;
    }
    final PsiElement newElement = findElementByFQName(qualifiedName);
    if (newElement != null) {
      myElementsCache.put(qualifiedName, newElement);
    }
    return newElement;
  }

  @Nullable
  private PsiElement findElementByFQName(final String qualifiedName) {
    final GlobalSearchScope searchScope = getSearchScope();
    final Collection<JavaFxQualifiedNamedElement> elements =
      StubIndex.getInstance().get(JavaFxQualifiedNameIndex.KEY, qualifiedName, myProject, searchScope);
    if (elements.size() != 0) {
      return elements.toArray(new PsiElement[elements.size()])[0];
    }
    final PsiClass aClass = myJavaPsiFacade.findClass(qualifiedName, searchScope);
    if (aClass != null) {
      return aClass;
    }
    return myJavaPsiFacade.findPackage(qualifiedName);
  }

  private GlobalSearchScope getSearchScope() {
    return GlobalSearchScope.allScope(myProject);
  }

  @Override
  public boolean processPackageFiles(final PsiPackage psiPackage,
                                     final PsiScopeProcessor processor,
                                     final ResolveState state,
                                     final PsiElement lastParent,
                                     final PsiElement place) {
    final VirtualFile[] directoriesByPackageName =
      PackageIndex.getInstance(myProject).getDirectoriesByPackageName(psiPackage.getQualifiedName(), false);
    for (VirtualFile dir : directoriesByPackageName) {
      final VirtualFile[] children = dir.getChildren();
      for (VirtualFile child : children) {
        final PsiFile file = PsiManager.getInstance(myProject).findFile(child);
        if (file != null) {
          if (!file.processDeclarations(processor, state, psiPackage, place)) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
