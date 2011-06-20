package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Krasilschikov
 */
public abstract class AbstractMvcPsiNodeDescriptor extends AbstractPsiBasedNode<NodeId> {
  public static final int FOLDER = 100;
  public static final int FILE = 110;
  public static final int CLASS = 5;
  public static final int FIELD = 7;
  public static final int METHOD = 10;
  public static final int DOMAIN_CLASSES_FOLDER = 20;
  public static final int CONTROLLERS_FOLDER = 30;
  public static final int VIEWS_FOLDER = 40;
  public static final int SERVICES_FOLDER = 50;
  public static final int CONFIG_FOLDER = 60;
  public static final int OTHER_GRAILS_APP_FOLDER = 64;
  public static final int WEB_APP_FOLDER = 65;
  public static final int SRC_FOLDERS = 70;
  public static final int TESTS_FOLDER = 80;
  public static final int TAGLIB_FOLDER = 90;

  private final Module myModule;
  private final int myWeight;

  protected AbstractMvcPsiNodeDescriptor(@NotNull final Module module,
                                         @Nullable final ViewSettings viewSettings,
                                         @NotNull final NodeId nodeId, int weight) {
    super(module.getProject(), nodeId, viewSettings);
    myModule = module;
    myWeight = weight;
  }

  @NonNls
  protected abstract String getTestPresentationImpl(@NotNull final NodeId nodeId,
                                                    @NotNull final PsiElement psiElement);

  @Override
  public final boolean contains(@NotNull final VirtualFile file) {
    return isValid() && containsImpl(file);
  }

  protected boolean containsImpl(@NotNull final VirtualFile file) {
    return super.contains(file);
  }

  @Nullable
  protected PsiElement extractPsiFromValue() {
    final NodeId nodeId = getValue();
    return nodeId != null ? nodeId.getPsiElement() : null;
  }

  @Override
  public final String getTestPresentation() {
    final PsiElement psi = extractPsiFromValue();
    if (psi == null || !psi.isValid() || !isValid()) {
      return "null";
    }

    return getTestPresentationImpl(getValue(), psi);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    if (!isValid()) {
      return null;
    }
    final PsiElement psiElement = extractPsiFromValue();
    assert psiElement != null;

    if (psiElement instanceof PsiFileSystemItem) {
      return ((PsiFileSystemItem)psiElement).getVirtualFile();
    }
    return psiElement.getContainingFile().getVirtualFile();
  }

  protected void updateImpl(final PresentationData data) {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement instanceof NavigationItem) {
      final ItemPresentation presentation = ((NavigationItem)psiElement).getPresentation();
      assert presentation != null;

      data.setPresentableText(presentation.getPresentableText());
    }
  }

  @Override
  public final int getTypeSortWeight(final boolean sortByType) {
    return myWeight;
  }

  protected boolean hasProblemFileBeneath() {
    return WolfTheProblemSolver.getInstance(getProject()).hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
        return contains(virtualFile);
      }
    });
  }

  public boolean isValid() {
    final PsiElement psiElement = extractPsiFromValue();
    return psiElement != null && psiElement.isValid();
  }
}
