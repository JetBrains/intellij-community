/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;

public class JavaFxWrapWithDefineIntention extends PsiElementBaseIntentionAction {
  private final XmlTag myTag;
  private final String myId;

  public JavaFxWrapWithDefineIntention(@NotNull XmlTag tag, @NotNull String id) {
    myTag = tag;
    myId = id;
  }

  @NotNull
  @Override
  public String getFamilyName() {
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
}
