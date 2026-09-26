// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

public final class JavaFxWrapWithDefineIntention extends PsiElementBaseIntentionAction {
  private final XmlTag myTag;
  private final String myId;

  public JavaFxWrapWithDefineIntention(@NotNull XmlTag tag, @NotNull String id) {
    myTag = tag;
    myId = id;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaFXBundle.message("javafx.wrap.with.fx.define.intention.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    setText( JavaFXBundle.message("javafx.wrap.id.with.fx.define.intention",myId));
    return myTag.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final XmlTag tagFromText = XmlElementFactory.getInstance(project).createTagFromText("<" + FxmlConstants.FX_DEFINE + "/>");
    tagFromText.addSubTag(myTag, true);
    myTag.replace(tagFromText);
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new JavaFxWrapWithDefineIntention(PsiTreeUtil.findSameElementInCopy(myTag, target), myId);
  }
}
