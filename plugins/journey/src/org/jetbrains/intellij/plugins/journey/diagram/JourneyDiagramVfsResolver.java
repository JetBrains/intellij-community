package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramVfsResolver;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.nio.file.Path;

// TODO
class JourneyDiagramVfsResolver implements DiagramVfsResolver<JourneyNodeIdentity> {

  private static final String OFFSET_DELIMITER = ":";
  private static final String METHOD_DELIMITER = "#";

  @Override
  public @Nullable String getQualifiedName(@Nullable JourneyNodeIdentity identity) {
    if (identity == null) {
      return null;
    }
    PsiElement element = identity.element();
    String psiQualifiedname = getPsiFqnRecursive(element);
    if (psiQualifiedname != null) return psiQualifiedname;
    return psiElementToPathAndOffsetFQN(element);
  }

  public @Nullable String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PsiMethod psiMethod) {
      if (psiMethod.getContainingClass() != null) return psiMethod.getContainingClass().getQualifiedName() + METHOD_DELIMITER + psiMethod.getName();
    }
    if (element instanceof PsiClass psiClass) return psiClass.getQualifiedName();
    if (element instanceof PsiPackage psiPackage) return psiPackage.getQualifiedName();
    if (element instanceof PsiClassOwner psiClassOwner) return psiClassOwner.getVirtualFile().getCanonicalPath();
    return null;
  }

  private @Nullable String getPsiFqnRecursive(@NotNull PsiElement element) {
    var parent = element;
    String result = null;
    while (parent != null && result == null) {
      result = getQualifiedName(parent);
      parent = parent.getParent();
    }
    return result;
  }

  private static @Nullable String psiElementToPathAndOffsetFQN(@NotNull PsiElement element) {
    PsiFile filpsiFile = element.getContainingFile();
    if (filpsiFile == null) return null;
    VirtualFile virtualFile = filpsiFile.getVirtualFile();
    if (virtualFile == null) return null;
    String fileId = virtualFile.getCanonicalPath();
    int offset = element.getTextOffset();
    return fileId + OFFSET_DELIMITER + offset;
  }

  @Override
  public @Nullable JourneyNodeIdentity resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
    return ReadAction.compute(() -> {
      PsiElement element = resolveElementByFQN2(fqn, project);
      if (element == null) return null;
      PsiElement parent = PsiUtil.tryFindParentOrNull(element, it -> it instanceof PsiMember);
      if (parent != null) {
        return new JourneyNodeIdentity(parent);
      }
      Messages.showErrorDialog("Cannot resolve element by FQN: " + fqn, "Error");
      return null;
    });
  }

  public @Nullable PsiElement resolveElementByFQN2(@NotNull String fqn, @NotNull Project project) {
    if (fqn.contains(OFFSET_DELIMITER)) {
      String path = fqn.substring(0, fqn.indexOf(OFFSET_DELIMITER));
      String offset = fqn.substring(fqn.indexOf(OFFSET_DELIMITER) + OFFSET_DELIMITER.length());
      PsiElement file = resolveElementByFQN2(path, project);
      if (file != null) {
        return file.findElementAt(Integer.parseInt(offset));
      }
      else {
        return null;
      }
    }
    if (fqn.contains(METHOD_DELIMITER)) {
      String path = fqn.substring(0, fqn.indexOf(METHOD_DELIMITER));
      String methodName = fqn.substring(fqn.indexOf(METHOD_DELIMITER) + METHOD_DELIMITER.length());
      PsiElement file = resolveElementByFQN2(path, project);
      if (file instanceof PsiClass psiClass) {
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
        if (methods.length == 0) {
          System.out.println("No method named " + methodName + " in class " + psiClass.getQualifiedName());
        }
        if (methods.length > 1) {
          System.out.println("More than one method named " + methodName + " in class " + psiClass.getQualifiedName());
        }
        if (methods.length > 0) {
          return methods[0];
        }
      }
      return null;
    }
      if (Strings.containsAnyChar(fqn, "/\\")) return resolveByPath(fqn, project);
      final var fromFqn = resolveByFQN(fqn, project);
      return fromFqn != null ? fromFqn : resolveByPath(fqn, project);
  }

  private static @Nullable PsiElement resolveByFQN(@NotNull String fqn, @NotNull Project project) {
    final var facadeEx = JavaPsiFacade.getInstance(project);
    final var psiClass = facadeEx.findClass(fqn, GlobalSearchScope.allScope(project));
    return psiClass == null ? facadeEx.findPackage(fqn) : psiClass;
  }

  private static @Nullable PsiFile resolveByPath(@NotNull String path, @NotNull Project project) {
    final var file = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path));
    if (file == null) return null;
    return PsiManager.getInstance(project).findFile(file);
  }
}
