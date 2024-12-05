package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramVfsResolver;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.intellij.plugins.journey.JourneyDataKeys;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class JourneyDiagramVfsResolver implements DiagramVfsResolver<JourneyNodeIdentity> {

  private static final Logger LOG = Logger.getInstance(JourneyDiagramVfsResolver.class);

  private final DiagramVfsResolver<PsiElement> myPsiVfsResolver;

  public JourneyDiagramVfsResolver() {
    this(
      path -> VirtualFileManager.getInstance().findFileByNioPath(path),
      (project1) -> JavaPsiFacade.getInstance(project1)
    );
  }

  @VisibleForTesting
  public JourneyDiagramVfsResolver(
    Function<Path, VirtualFile> loadVirtualFile,
    Function<Project, JavaPsiFacade> psiFacadeProvider
  ) {
    myPsiVfsResolver = new VfsResolverChain<>(List.of(
      new RecursivePsiVfsResolver(new JavaPsiVfsResolver(psiFacadeProvider)),
      new FilePathAndOffsetPsiVfsResolver(loadVirtualFile)
    ));
  }

  @Override
  public @Nullable String getQualifiedName(@Nullable JourneyNodeIdentity identity) {
    if (identity == null) {
      return null;
    }
    PsiElement element = identity.getOriginalElement();
    // PSI calculations are "slow operations", but we need to do it in to satisfy the diagram framework.
    try (var ignored = SlowOperations.startSection(JourneyDataKeys.JOURNEY_SLOW_OPERATIONS)) {
      return ReadAction.compute(() -> myPsiVfsResolver.getQualifiedName(element));
    }
  }

  @Override
  public @Nullable JourneyNodeIdentity resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
    PsiElement element;
    // PSI calculations are "slow operations", but we need to do it in to satisfy the diagram framework.
    try (var ignored = SlowOperations.startSection(JourneyDataKeys.JOURNEY_SLOW_OPERATIONS)) {
      element = ReadAction.compute(() -> myPsiVfsResolver.resolveElementByFQN(fqn, project));
    }
    if (element == null) return null;
    return new JourneyNodeIdentity(element);
  }

  public static final class JavaPsiVfsResolver implements DiagramVfsResolver<PsiElement> {
    private static final String METHOD_DELIMITER = "#";
    private final Function<Project, JavaPsiFacade> psiFacadeProvider;

    public JavaPsiVfsResolver(Function<Project, JavaPsiFacade> psiFacadeProvider) { this.psiFacadeProvider = psiFacadeProvider; }

    @Override
    public @Nullable String getQualifiedName(@Nullable PsiElement element) {
      if (element instanceof PsiClass psiClass) return psiClass.getQualifiedName();
      if (element instanceof PsiMethod psiMethod) {
        if (psiMethod.getContainingClass() != null) {
          return psiMethod.getContainingClass().getQualifiedName() + METHOD_DELIMITER + psiMethod.getName();
        }
      }
      if (element instanceof PsiField psiField) {
        if (psiField.getContainingClass() != null) {
          return psiField.getContainingClass().getQualifiedName() + METHOD_DELIMITER + psiField.getName();
        }
      }
      return null;
    }

    @Override
    public @Nullable PsiElement resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
      if (fqn.contains(METHOD_DELIMITER)) {
        int delimiterPosition = fqn.indexOf(METHOD_DELIMITER);
        String classFQN = fqn.substring(0, delimiterPosition);
        PsiClass psiClass = resolveClassByFQN(classFQN, project);
        String methodName = fqn.substring(delimiterPosition + METHOD_DELIMITER.length());
        return findMethodByName(psiClass, methodName);
      }
      return resolveClassByFQN(fqn, project);
    }

    private static @Nullable PsiMethod findMethodByName(PsiClass psiClass, String methodName) {
      if (psiClass == null) {
        return null;
      }
      PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
      if (methods.length == 0) {
        LOG.warn("No method named " + methodName + " in class " + psiClass.getQualifiedName());
      }
      if (methods.length > 1) {
        LOG.warn("More than one method named " + methodName + " in class " + psiClass.getQualifiedName());
      }
      if (methods.length > 0) {
        return methods[0];
      }
      return null;
    }

    private @Nullable PsiClass resolveClassByFQN(@NotNull String fqn, @NotNull Project project) {
      return psiFacadeProvider.apply(project).findClass(fqn, GlobalSearchScope.allScope(project));
    }
  }

  public static final class RecursivePsiVfsResolver implements DiagramVfsResolver<PsiElement> {
    private final DiagramVfsResolver<PsiElement> myDelegate;

    public RecursivePsiVfsResolver(DiagramVfsResolver<PsiElement> delegate) { myDelegate = delegate; }

    @Override
    public @Nullable String getQualifiedName(@Nullable PsiElement element) {
      var parent = element;
      String result = null;
      while (parent != null && result == null) {
        result = myDelegate.getQualifiedName(parent);
        parent = parent.getParent();
      }
      return result;
    }

    @Override
    public @Nullable PsiElement resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
      return myDelegate.resolveElementByFQN(fqn, project);
    }
  }

  public static final class VfsResolverChain<T> implements DiagramVfsResolver<T> {
    private final List<DiagramVfsResolver<T>> myResolvers;

    public VfsResolverChain(List<DiagramVfsResolver<T>> resolvers) {
      myResolvers = resolvers;
    }

    @Override
    public @Nullable String getQualifiedName(@Nullable T element) {
      for (DiagramVfsResolver<T> resolver : myResolvers) {
        String result = resolver.getQualifiedName(element);
        if (result != null) return result;
      }
      return null;
    }

    @Override
    public @Nullable T resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
      for (DiagramVfsResolver<T> resolver : myResolvers) {
        T result = resolver.resolveElementByFQN(fqn, project);
        if (result != null) return result;
      }
      return null;
    }
  }

  public static final class FilePathAndOffsetPsiVfsResolver implements DiagramVfsResolver<PsiElement> {
    private static final String OFFSET_DELIMITER = ":";

    private final Function<Path, VirtualFile> loadVirtualFile;

    public FilePathAndOffsetPsiVfsResolver(Function<Path, VirtualFile> loadVirtualFile) { this.loadVirtualFile = loadVirtualFile; }

    @Override
    public @Nullable String getQualifiedName(@Nullable PsiElement element) {
      if (element == null) return null;
      PsiFile filpsiFile = element.getContainingFile();
      if (filpsiFile == null) return null;
      VirtualFile virtualFile = filpsiFile.getVirtualFile();
      if (virtualFile == null) return null;
      String filePath = virtualFile.getCanonicalPath();
      if (filePath == null) return null;
      int offset = element.getTextOffset();
      return filePath + OFFSET_DELIMITER + offset;
    }

    @Override
    public @Nullable PsiElement resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
      if (fqn.contains(OFFSET_DELIMITER)) {
        String path = fqn.substring(0, fqn.lastIndexOf(OFFSET_DELIMITER));
        String offset = fqn.substring(fqn.indexOf(OFFSET_DELIMITER) + OFFSET_DELIMITER.length());
        PsiElement file = resolveElementByFQN(path, project);
        if (file != null) {
          return file.findElementAt(Integer.parseInt(offset));
        }
        else {
          return null;
        }
      }
      return resolveByPath(fqn, project);
    }

    private @Nullable PsiFile resolveByPath(@NotNull String path, @NotNull Project project) {
      Path path1 = Path.of(path);
      final var file = loadVirtualFile.apply(path1);
      if (file == null) return null;
      return PsiManager.getInstance(project).findFile(file);
    }
  }

}
