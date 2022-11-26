// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationBuilder;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.SingletonNotificationManager;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UExpression;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;
import static com.intellij.patterns.uast.UastPatterns.uExpression;
import static com.intellij.psi.UastReferenceRegistrar.registerUastReferenceProvider;

public class NotificationGroupIdReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerUastReferenceProvider(registrar,
                                  injectionHostUExpression()
                                    .sourcePsiFilter(psi -> PsiUtil.isPluginProject(psi.getProject()))
                                    .andOr(
                                      uExpression().constructorParameter(0, Notification.class.getName()),
                                      uExpression().constructorParameter(0, NotificationBuilder.class.getName()),
                                      uExpression().methodCallParameter(0, psiMethod().withName("getNotificationGroup")
                                        .definedInClass(NotificationGroupManager.class.getName())),
                                      uExpression().constructorParameter(0, SingletonNotificationManager.class.getName())
                                    ),

                                  new UastInjectionHostReferenceProvider() {
                                    @Override
                                    public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                                                                  @NotNull PsiLanguageInjectionHost host,
                                                                                                  @NotNull ProcessingContext context) {
                                      return new PsiReference[]{new NotificationGroupIdReference(host)};
                                    }
                                  }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  private static class NotificationGroupIdReference extends ExtensionReferenceBase {

    private NotificationGroupIdReference(PsiElement element) {
      super(element);
    }

    @Override
    protected String getExtensionPointFqn() {
      return "com.intellij.notificationGroup";
    }

    @Override
    public @InspectionMessage @NotNull String getUnresolvedMessagePattern() {
      return DevKitBundle.message("code.convert.notification.group.cannot.resolve", getValue());
    }

    @Override
    public Object @NotNull [] getVariants() {
      final List<LookupElement> variants = Collections.synchronizedList(new SmartList<>());
      processCandidates(extension -> {

        final GenericAttributeValue<String> id = extension.getId();
        if (id == null || extension.getXmlElement() == null) return true;

        final String value = id.getStringValue();
        if (value == null) return true;

        final String toolwindowId = getAttributeValue(extension, "toolWindowId");
        final String displayType = getAttributeValue(extension, "displayType");
        final String logByDefault = getAttributeValue(extension, "isLogByDefault");
        Icon logIcon = !"false".equals(logByDefault) ? AllIcons.Ide.Notification.NoEvents : null;

        variants.add(LookupElementBuilder.create(extension.getXmlElement(), value)
                       .withTailText(toolwindowId != null ? " (" + toolwindowId + ")" : "")
                       .withTypeText(displayType, logIcon, false)
                       .withTypeIconRightAligned(true));
        return true;
      });
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }
  }
}
