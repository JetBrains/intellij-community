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
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
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

  public boolean useDefaultInjector(final PsiLanguageInjectionHost host) {
    return false;
  }

  @Nullable
  @Override
  public BaseInjection findCommentInjection(@NotNull PsiElement host, @Nullable Ref<PsiElement> commentRef) {
    return InjectorUtils.findCommentInjection(host, "comment", commentRef);
  }

  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  public BaseInjection createInjection(final Element element) {
    return new BaseInjection(getId());
  }

  public void setupPresentation(final BaseInjection injection, final SimpleColoredText presentation, final boolean isSelected) {
    presentation.append(injection.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[0];
  }

  public AnAction[] createAddActions(final Project project, final Consumer<BaseInjection> consumer) {
    return new AnAction[] { createDefaultAddAction(project, consumer, this) };
  }

  public AnAction createEditAction(final Project project, final Factory<BaseInjection> producer) {
    return createDefaultEditAction(project, producer);
  }

  public static AnAction createDefaultEditAction(final Project project, final Factory<BaseInjection> producer) {
    return new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final BaseInjection originalInjection = producer.create();
        final BaseInjection newInjection = showDefaultInjectionUI(project, originalInjection.copy());
        if (newInjection != null) {
          originalInjection.copyFrom(newInjection);
        }
      }
    };
  }

  public static AnAction createDefaultAddAction(final Project project,
                                                final Consumer<BaseInjection> consumer,
                                                final AbstractLanguageInjectionSupport support) {
    final String supportTitle = StringUtil.capitalize(support.getId());
    Icon icon = FileTypeManager.getInstance().getFileTypeByExtension(support.getId()).getIcon();
    return new AnAction("Generic "+ supportTitle, null, icon) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final BaseInjection injection = new BaseInjection(support.getId());
        injection.setDisplayName("New "+ supportTitle +" Injection");
        final BaseInjection newInjection = showDefaultInjectionUI(project, injection);
        if (newInjection != null) {
          consumer.consume(injection);
        }
      }
    };
  }

  @Nullable
  protected static BaseInjection showDefaultInjectionUI(final Project project, BaseInjection injection) {
    final BaseInjectionPanel panel = new BaseInjectionPanel(injection, project);
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    LanguageInjectionSupport support = InjectorUtils.findInjectionSupport(injection.getSupportId());
    if (support instanceof AbstractLanguageInjectionSupport) {
      builder.setHelpId(((AbstractLanguageInjectionSupport)support).getHelpId());
    }
    builder.addOkAction();
    builder.addCancelAction();
    builder.setDimensionServiceKey("#org.intellij.plugins.intelliLang.inject.config.ui.BaseInjectionDialog");
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(EditInjectionSettingsAction.EDIT_INJECTION_TITLE);
    builder.setOkOperation(new Runnable() {
      public void run() {
        try {
          panel.apply();
          builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        }
        catch (Exception e) {
          final Throwable cause = e.getCause();
          final String message = e.getMessage() + (cause != null? "\n  "+cause.getMessage():"");
          Messages.showErrorDialog(project, message, "Unable to Save");
        }
      }
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      return injection;
    }
    return null;
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
