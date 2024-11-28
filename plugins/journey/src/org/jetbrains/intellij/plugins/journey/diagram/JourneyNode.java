package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagram.DiagramNodeBase;
import com.intellij.diagram.DiagramProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.editor.ScrollType.CENTER_UP;

@SuppressWarnings("HardCodedStringLiteral")
public class JourneyNode extends DiagramNodeBase<JourneyNodeIdentity> {

  @NotNull
  private final JourneyNodeIdentity identity;
  @Nullable private final String myTitle;

  public JourneyNode(
    @NotNull DiagramProvider<JourneyNodeIdentity> provider,
    @NotNull JourneyNodeIdentity identity,
    @Nullable String title
  ) {
    super(provider);
    this.identity = identity;
    myTitle = title;
  }

  @Override
  public @NotNull JourneyNodeIdentity getIdentifyingElement() {
    return identity;
  }

  @Override
  public @Nullable @Nls String getTooltip() {
    return "Journey node tooltip " + identity.calculatePsiElement().getText();
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Process.ProgressResume; // TODO
  }

  @Override
  public @Nullable SimpleColoredText getPresentableTitle() {
    if (myTitle == null) return null;
    return new SimpleColoredText(myTitle, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    final var that = (JourneyNode)obj;
    PsiElement psi1 = this.identity.calculatePsiElement();
    PsiElement psi2 = that.identity.calculatePsiElement();

    if (psi1 == psi2) {
      return true;
    }

    if (psi1 instanceof PsiMember psiMember1 && psi2 instanceof PsiMember psiMember2) {
      Boolean o = ReadAction.compute(() -> Objects.equals(psiMember1.getContainingClass(), (psiMember2.getContainingClass())));
      if (!o) return false;
    }
    return super.equals(obj);
  }
}
