package org.editorconfig.annotations;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import icons.EditorconfigIcons;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigAnnotator implements Annotator {
  private static final String EDITOR_CONFIG_ACCEPTED = "editor.config.accepted";

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    PsiFile file = ObjectUtils.tryCast(element, PsiFile.class);
    if (file == null) {
      return;
    }
    final Project project = file.getProject();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    if (!Utils.isEnabled(settings) || PropertiesComponent.getInstance(project).getBoolean(EDITOR_CONFIG_ACCEPTED, false)) return;
    final List<EditorConfig.OutPair> pairs = SettingsProviderComponent.getInstance().getOutPairs(project, Utils.getFilePath(project, file.getVirtualFile()));
    if (!pairs.isEmpty()) {
      final Annotation annotation = holder.createInfoAnnotation(file, "EditorConfig is overriding Code Style settings for this file");
      annotation.setFileLevelAnnotation(true);
      annotation.setGutterIconRenderer(new MyGutterIconRenderer());
      annotation.registerFix(new IntentionAction() {
        @NotNull
        @Override
        public String getText() {
          return "Disable EditorConfig support";
        }

        @NotNull
        @Override
        public String getFamilyName() {
          return "EditorConfig";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          settings.getCustomSettings(EditorConfigSettings.class).ENABLED = false;
          DaemonCodeAnalyzer.getInstance(project).restart();
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }
      });
      annotation.registerFix(new IntentionAction() {
        @NotNull
        @Override
        public String getText() {
          return "Dismiss";
        }

        @NotNull
        @Override
        public String getFamilyName() {
          return "EditorConfig";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          PropertiesComponent.getInstance(project).setValue(EDITOR_CONFIG_ACCEPTED, "true");
          DaemonCodeAnalyzer.getInstance(project).restart();
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }
      });
    }
  }

  private static class MyGutterIconRenderer extends GutterIconRenderer {
    @NotNull
    @Override
    public Icon getIcon() {
      return EditorconfigIcons.Editorconfig;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyGutterIconRenderer;
    }

    @Override
    public int hashCode() {
      return EditorconfigIcons.Editorconfig.hashCode();
    }
  }
}
