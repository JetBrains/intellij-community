// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.UIThemeProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.impl.BundledKeymapBean;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PluginModuleConvertToGradleStartupActivity implements StartupActivity.Background {
  @NonNls
  private static final String ID = "Migrate DevKit plugin to Gradle";

  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup(ID);

  private static final String DO_NOT_SHOW_AGAIN_SETTING = "PluginModuleConvertToGradleStartupActivity.DoNotShowAgain";

  @Override
  public void runActivity(@NotNull Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() ||
        application.isHeadlessEnvironment()) {
      return;
    }

    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    if (propertiesComponent.isTrueValue(DO_NOT_SHOW_AGAIN_SETTING)) {
      return;
    }

    Set<Module> devkitModules = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (PluginModuleType.isOfType(module)) {
        devkitModules.add(module);
        if (devkitModules.size() > 1) {
          break;
        }
      }
    }
    if (!isSimpleSingleModulePlugin(project, devkitModules)) return;

    UIUtil.invokeLaterIfNeeded(() -> NOTIFICATION_GROUP
      .createNotification(DevKitBundle.message("convert.devkit.to.gradle.notification"), DevKitBundle
                                                                             .message("convert.devkit.to.gradle.notification.content"), NotificationType.INFORMATION)
      .addAction(NotificationAction.createSimpleExpiring(
        DevKitBundle.message("convert.devkit.to.gradle.notification.link.title"),
        () -> BrowserUtil.browse("https://plugins.jetbrains.com/docs/intellij/gradle-prerequisites.html?from=DevkitConvertToGradleNotification" +
                                 "#adding-gradle-support-to-an-existing-devkit-based-intellij-platform-plugin")))
      .addAction(NotificationAction.createSimpleExpiring(DevKitBundle.message("convert.devkit.to.gradle.notification.do.not.show.again"), () -> propertiesComponent.setValue(DO_NOT_SHOW_AGAIN_SETTING, true)))
      .setIcon(AllIcons.Nodes.Plugin)
      .notify(project));
  }

  private static boolean isSimpleSingleModulePlugin(Project project, Set<Module> devkitModules) {
    if (devkitModules.size() != 1) return false;

    return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      final XmlFile pluginXml = PluginModuleType.getPluginXml(ContainerUtil.getOnlyItem(devkitModules));
      if (pluginXml == null || !DescriptorUtil.isPluginXml(pluginXml)) return false;

      final IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml);
      assert ideaPlugin != null;

      Extensions extensions = ContainerUtil.getOnlyItem(ideaPlugin.getExtensions());
      if (extensions == null) return false;

      final List<Extension> extensionList = extensions.collectExtensions();
      for (Extension extension : extensionList) {
        final ExtensionPoint extensionPoint = extension.getExtensionPoint();
        if (extensionPoint == null) return false;

        final String extensionPointName = extensionPoint.getEffectiveQualifiedName();
        if (!UIThemeProvider.EP_NAME.getName().equals(extensionPointName) &&
            !BundledKeymapBean.EP_NAME.getName().equals(extensionPointName)) {
          return false;
        }
      }
      return !extensionList.isEmpty();
    });
  }
}
