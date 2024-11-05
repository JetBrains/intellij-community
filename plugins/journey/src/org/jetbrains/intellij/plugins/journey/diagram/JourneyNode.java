package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramNodeBase;
import com.intellij.diagram.DiagramProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

@SuppressWarnings("HardCodedStringLiteral")
public class JourneyNode extends DiagramNodeBase<JourneyNodeIdentity> {

  @NotNull
  private final JourneyNodeIdentity identity;

  public JourneyNode(@NotNull JourneyNodeIdentity identity, @NotNull DiagramProvider<JourneyNodeIdentity> provider) {
    super(provider);
    this.identity = identity;
  }

  @Override
  public @NotNull JourneyNodeIdentity getIdentifyingElement() {
    return identity;
  }

  @Override
  public @Nullable @Nls String getTooltip() {
    return "Journey node tooltip " + identity.element().getText();
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Process.ProgressResume; // TODO
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    final var that = (JourneyNode)obj;
    PsiElement psi1 = this.identity.element();
    PsiElement psi2 = that.identity.element();
    if (psi1 instanceof PsiMethod && psi2 instanceof PsiMethod) {
      Boolean o = ReadAction.compute(() -> Objects.equals(((PsiMethod)psi1).getContainingClass(), ((PsiMethod)psi2).getContainingClass()));
      if (!o) return false;
    }
    return super.equals(obj);
  }

}
