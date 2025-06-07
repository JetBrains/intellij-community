// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.create;

import com.intellij.editorconfig.common.EditorConfigBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;
import java.util.List;

public class CreateEditorConfigDialog extends DialogWrapper {

  private       CreateEditorConfigForm myForm;
  private final Project                myProject;

  protected CreateEditorConfigDialog(@NotNull Project project) {
    super(false);
    myProject = project;
    init();
    setTitle(EditorConfigBundle.message("dialog.title.new.editorconfig.file"));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    myForm = new CreateEditorConfigForm(myProject);
    return myForm.getTopPanel();
  }

  public EditorConfigPropertyKind[] getPropertyKinds() {
    EnumSet<EditorConfigPropertyKind> kinds = EnumSet.noneOf(EditorConfigPropertyKind.class);
    if (myForm.isStandardProperties()) {
      kinds.add(EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD);
    }
    if (myForm.isIntelliJProperties()) {
      kinds.add(EditorConfigPropertyKind.LANGUAGE);
      kinds.add(EditorConfigPropertyKind.GENERIC);
      kinds.add(EditorConfigPropertyKind.COMMON);
    }
    return kinds.toArray(new EditorConfigPropertyKind[0]);
  }

  public boolean isRoot() {
    return myForm.isRoot();
  }

  public boolean isCommentProperties() {
    return myForm.isCommentProperties();
  }

  public List<Language> getLanguages() {
    return myForm.getSelectedLanguages();
  }

  @Override
  protected @NonNls @Nullable String getHelpId() {
    return "reference.code.style.editorconfig";
  }
}
