/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.BaseInjectionPanel;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Gregory.Shrago
 */
public abstract class AbstractLanguageInjectionSupport extends LanguageInjectionSupport {

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return false;
  }

  @Override
  public boolean useDefaultInjector(final PsiLanguageInjectionHost host) {
    return false;
  }

  @Override
  public boolean useDefaultCommentInjector() {
    return true;
  }

  @Nullable
  @Override
  public BaseInjection findCommentInjection(@NotNull PsiElement host, @Nullable Ref<? super PsiElement> commentRef) {
    return InjectorUtils.findCommentInjection(host, "comment", commentRef);
  }

  @Override
  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  @Override
  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  @Override
  public boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  @Override
  public BaseInjection createInjection(final Element element) {
    return new BaseInjection(getId());
  }

  @Override
  public void setupPresentation(final BaseInjection injection, final SimpleColoredText presentation, final boolean isSelected) {
    presentation.append(injection.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[0];
  }

  @Override
  public AnAction[] createAddActions(final Project project, final Consumer<? super BaseInjection> consumer) {
    return new AnAction[] { createDefaultAddAction(project, consumer, this) };
  }

  @Override
  public AnAction createEditAction(final Project project, final Factory<? extends BaseInjection> producer) {
    return createDefaultEditAction(project, producer);
  }

  public static AnAction createDefaultEditAction(Project project, Factory<? extends BaseInjection> producer) {
    return DumbAwareAction.create(e -> perform(project, producer));
  }

  protected static void perform(Project project, Factory<? extends BaseInjection> producer) {
    BaseInjection originalInjection = producer.create();
    BaseInjection newInjection = showDefaultInjectionUI(project, originalInjection.copy());
    if (newInjection != null) {
      originalInjection.copyFrom(newInjection);
    }
  }

  public static AnAction createDefaultAddAction(final Project project,
                                                final Consumer<? super BaseInjection> consumer,
                                                final AbstractLanguageInjectionSupport support) {
    final String supportTitle = StringUtil.capitalize(support.getId());
    Icon icon = FileTypeManager.getInstance().getFileTypeByExtension(support.getId()).getIcon();
    AnAction action = DumbAwareAction.create(e -> {
      BaseInjection injection = new BaseInjection(support.getId());
      injection.setDisplayName("New " + supportTitle + " Injection");
      final BaseInjection newInjection = showDefaultInjectionUI(project, injection);
      if (newInjection != null) {
        consumer.consume(injection);
      }
    });
    action.getTemplatePresentation().setText("Generic " + supportTitle);
    action.getTemplatePresentation().setIcon(icon);
    return action;
  }

  @Nullable
  protected static BaseInjection showDefaultInjectionUI(final Project project, BaseInjection injection) {
    final BaseInjectionPanel panel = new BaseInjectionPanel(injection, project);
    panel.reset();
    String dimensionServiceKey = "#org.intellij.plugins.intelliLang.inject.config.ui.BaseInjectionDialog";
    LanguageInjectionSupport support = InjectorUtils.findInjectionSupport(injection.getSupportId());
    String helpId = support instanceof AbstractLanguageInjectionSupport ? ((AbstractLanguageInjectionSupport)support).getHelpId() : null;
    return showEditInjectionDialog(project, panel, dimensionServiceKey, helpId) ? injection : null;
  }

  protected static boolean showEditInjectionDialog(@NotNull Project project,
                                                   @NotNull AbstractInjectionPanel panel,
                                                   @Nullable String dimensionServiceKey, @Nullable String helpId) {
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setHelpId(helpId);
    builder.addOkAction();
    builder.addCancelAction();
    builder.setDimensionServiceKey(dimensionServiceKey);
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(EditInjectionSettingsAction.EDIT_INJECTION_TITLE);
    builder.setOkOperation(() -> {
      try {
        panel.apply();
        builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
      }
      catch (Exception e) {
        final Throwable cause = e.getCause();
        final String message = e.getMessage() + (cause != null? "\n  "+cause.getMessage():"");
        Messages.showErrorDialog(project, message, "Unable to Save");
      }
    });
    return builder.show() == DialogWrapper.OK_EXIT_CODE;
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof LanguageInjectionSupport && getId().equals(((LanguageInjectionSupport)obj).getId());
  }

  @Nullable
  public String getHelpId() {
    return null;
  }
}
