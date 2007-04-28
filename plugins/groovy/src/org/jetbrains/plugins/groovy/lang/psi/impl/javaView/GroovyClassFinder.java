package org.jetbrains.plugins.groovy.lang.psi.impl.javaView;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.caches.GroovyCachesManager;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.*;

/**
 * @author ven
 */
public class GroovyClassFinder implements ProjectComponent, PsiElementFinder {
  private Project myProject;
  private Map<GroovyFile, GrJavaFile> myJavaFiles = new WeakHashMap<GroovyFile, GrJavaFile>();
  private GroovyClassFinder.MyVFSListener myVfsListener;

  public GroovyClassFinder(Project project) {
    myProject = project;
  }

  @Nullable
  public PsiClass findClass(@NotNull String qualifiedName, GlobalSearchScope scope) {
    GrTypeDefinition typeDef = GroovyCachesManager.getInstance(myProject).getClassByName(qualifiedName, scope);
    if (typeDef == null) return null;
    return new GrJavaClass(getJavaFile((GroovyFile) typeDef.getContainingFile()), typeDef);
  }

  private GrJavaFile getJavaFile(GroovyFile file) {
    GrJavaFile javaFile = myJavaFiles.get(file);
    if (javaFile == null) {
      javaFile = new GrJavaFile(file);
      myJavaFiles.put(file, javaFile);
    }

    return javaFile;
  }

  @NotNull
  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    GrTypeDefinition[] typeDefs = GroovyCachesManager.getInstance(myProject).getClassesByName(qualifiedName, scope);
    if (typeDefs.length == 0) return PsiClass.EMPTY_ARRAY;
    PsiClass[] result = new PsiClass[typeDefs.length];
    for (int i = 0; i < result.length; i++) {
      GrTypeDefinition typeDef = typeDefs[i];
      result[i] = new GrJavaClass(getJavaFile((GroovyFile) typeDef.getContainingFile()), typeDef);
    }

    return result;
  }

  @Nullable
  public PsiPackage findPackage(String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope) {
    return new PsiPackage[0];
  }

  @NotNull
  public PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (final PsiDirectory dir : psiPackage.getDirectories(scope)) {
      for (final PsiFile file : dir.getFiles()) {
        if (file instanceof GroovyFile) {
          result.addAll(Arrays.asList(getJavaFile((GroovyFile) file).getClasses()));
        }
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myVfsListener = new MyVFSListener();
        VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);
      }
    });
  }

  public void projectClosed() {
    VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy class finder";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  class MyVFSListener extends VirtualFileAdapter {
    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile vFile = event.getFile();
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
      if (psiFile instanceof GroovyFile) {
        GroovyFile groovyFile = (GroovyFile) psiFile;
        myJavaFiles.remove(groovyFile);
      }
    }
  }
}
