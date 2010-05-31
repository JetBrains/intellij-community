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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.BaseInjectionPanel;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class AbstractLanguageInjectionSupport extends LanguageInjectionSupport {

  public boolean useDefaultInjector(final PsiElement host) {
    return false;
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
    if (!ApplicationManagerEx.getApplicationEx().isInternal()) return AnAction.EMPTY_ARRAY;
    return new AnAction[] { createDefaultAddAction(project, consumer, this) };
  }

  public AnAction createEditAction(final Project project, final Factory<BaseInjection> producer) {
    return createDefaultEditAction(project, producer);
  }

  public static AnAction createDefaultEditAction(final Project project, final Factory<BaseInjection> producer) {
    return new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (!ApplicationManagerEx.getApplicationEx().isInternal()) return;
        final BaseInjection originalInjection = producer.create();
        final BaseInjection newInjection = showInjectionUI(project, originalInjection.copy());
        if (newInjection != null) {
          originalInjection.copyFrom(newInjection);
          originalInjection.initializePlaces(true);
        }
      }
    };
  }

  public static AnAction createDefaultAddAction(final Project project,
                                                final Consumer<BaseInjection> consumer,
                                                final AbstractLanguageInjectionSupport support) {
    return new AnAction("Generic "+ StringUtil.capitalize(support.getId()), null, Icons.FILE_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final BaseInjection injection = new BaseInjection(support.getId());
        injection.setDisplayName("New "+support.getId()+" Injection");
        final BaseInjection newInjection = showInjectionUI(project, injection);
        if (newInjection != null) {
          consumer.consume(injection);
        }
      }
    };
  }

  @Nullable
  private static BaseInjection showInjectionUI(Project project, BaseInjection injection) {
    final BaseInjectionPanel panel = new BaseInjectionPanel(injection, project);
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setHelpId("reference.settings.injection.language.injection.settings.java.parameter");
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
          Messages.showErrorDialog(builder.getWindow(), message, "Unable to Save");
        }
      }
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      injection.initializePlaces(false);
      return injection;
    }
    return null;
  }
}
