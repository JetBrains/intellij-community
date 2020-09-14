// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.StatisticsCollectorType;

public class RegisterStatisticsCollectorFix extends AbstractRegisterFix {

  private final StatisticsCollectorType myCollectorType;

  public RegisterStatisticsCollectorFix(@NotNull PsiClass psiClass,
                                        @NotNull StatisticsCollectorType collectorType) {
    super(psiClass);
    myCollectorType = collectorType;
  }

  @Override
  protected String getType() {
    return DevKitBundle.message("new.statistics.collector.text");
  }

  @Override
  public void patchPluginXml(XmlFile pluginXml, PsiClass aClass) throws IncorrectOperationException {

    final XmlTag rootTag = pluginXml.getRootTag();
    if (rootTag != null && IdeaPlugin.TAG_NAME.equals(rootTag.getName())) {
      XmlTag extensions = rootTag.findFirstSubTag("extensions");
      if (extensions == null || !extensions.isPhysical()) {
        extensions = (XmlTag)rootTag.add(rootTag.createChildTag("extensions", rootTag.getNamespace(), null, false));
        extensions.setAttribute("defaultExtensionNs", Extensions.DEFAULT_PREFIX);
      }
      XmlTag extensionTag = (XmlTag)extensions.add(extensions.createChildTag(
        myCollectorType.getExtensionPoint(), extensions.getNamespace(), null, false));
      extensionTag.setAttribute(myCollectorType.getImplementationAttribute(), ClassUtil.getJVMClassName(aClass));
    }
  }
}
