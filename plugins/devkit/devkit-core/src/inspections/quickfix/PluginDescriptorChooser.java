/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.xml.util.IncludedXmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PluginDescriptorChooser {

  private static final Map<String, String> INTELLIJ_MODULES =
    ContainerUtil.<String, String>immutableMapBuilder()
      .put("intellij.platform.ide", "PlatformExtensions.xml")
      .put("intellij.platform.ide.impl", "PlatformExtensions.xml")
      .put("intellij.platform.lang", "LangExtensions.xml")
      .put("intellij.platform.lang.impl", "LangExtensions.xml")
      .put("intellij.platform.vcs", "VcsExtensions.xml")
      .put("intellij.platform.vcs.impl", "VcsExtensions.xml")
      .put("intellij.java", "IdeaPlugin.xml")
      .put("intellij.java.impl", "IdeaPlugin.xml")
      .put("intellij.java.analysis.impl", "IdeaPlugin.xml")
      .build();

  public static void show(final Project project,
                          final Editor editor,
                          final PsiFile file,
                          final Consumer<DomFileElement<IdeaPlugin>> consumer) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;
    List<DomFileElement<IdeaPlugin>> elements = DomService.getInstance().getFileElements(IdeaPlugin.class, project,
                                                                                         module.getModuleWithDependentsScope());

    elements = ContainerUtil.filter(elements, element -> {
      VirtualFile virtualFile = element.getFile().getVirtualFile();
      return virtualFile != null && ProjectRootManager.getInstance(project).getFileIndex().isInContent(virtualFile);
    });

    elements = findAppropriateIntelliJModule(module.getName(), elements);

    if (elements.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "Cannot find plugin descriptor");
      return;
    }

    if (elements.size() == 1) {
      consumer.consume(elements.get(0));
      return;
    }

    final BaseListPopupStep<PluginDescriptorCandidate> popupStep =
      new BaseListPopupStep<PluginDescriptorCandidate>("Choose Plugin Descriptor", createCandidates(module, elements)) {

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public Icon getIconFor(PluginDescriptorCandidate candidate) {
          return candidate.getIcon();
        }

        @NotNull
        @Override
        public String getTextFor(PluginDescriptorCandidate candidate) {
          return candidate.getText();
        }

        @Nullable
        @Override
        public ListSeparator getSeparatorAbove(PluginDescriptorCandidate candidate) {
          final String separatorText = candidate.getSeparatorText();
          if (separatorText != null) {
            return new ListSeparator(separatorText);
          }
          return null;
        }

        @Override
        public PopupStep onChosen(PluginDescriptorCandidate selectedValue, boolean finalChoice) {
          consumer.consume(selectedValue.myDomFileElement);
          return FINAL_CHOICE;
        }
      };
    JBPopupFactory.getInstance().createListPopup(popupStep).showInBestPositionFor(editor);
  }

  @NotNull
  public static Extensions findOrCreateExtensionsForEP(DomFileElement<IdeaPlugin> domFileElement, String epName) {
    final IdeaPlugin ideaPlugin = domFileElement.getRootElement();
    final List<Extensions> extensionsList = ideaPlugin.getExtensions();
    for (Extensions extensions : extensionsList) {
      if (extensions.getXmlTag() instanceof IncludedXmlTag) {
        continue;
      }
      String s = extensions.getDefaultExtensionNs().getStringValue();
      if (s != null && epName.startsWith(s)) {
        return extensions;
      }
    }

    Extensions extensions = ideaPlugin.addExtensions();
    final String epPrefix = StringUtil.getPackageName(epName);
    extensions.getDefaultExtensionNs().setStringValue(epPrefix);
    return extensions;
  }

  private static List<PluginDescriptorCandidate> createCandidates(final Module currentModule,
                                                                  List<DomFileElement<IdeaPlugin>> elements) {
    ModuleGrouper grouper = ModuleGrouper.instanceFor(currentModule.getProject());
    final List<String> groupPath = grouper.getGroupPath(currentModule);

    elements.sort((o1, o2) -> {
      // current module = first group
      final Module module1 = o1.getModule();
      final Module module2 = o2.getModule();

      if (!Comparing.equal(module1, module2)) {
        if (currentModule.equals(module1)) return -1;
        if (currentModule.equals(module2)) return 1;
      }

      if (module1 != null && module2 != null) {
        int groupComparison = Comparing.compare(groupMatchLevel(groupPath, grouper.getGroupPath(module2)),
                                                groupMatchLevel(groupPath, grouper.getGroupPath(module1)));
        if (groupComparison != 0) {
          return groupComparison;
        }
      }
      return ModulesAlphaComparator.INSTANCE.compare(module1, module2);
    });
    elements.sort((o1, o2) -> {
      if (!Comparing.equal(o1.getModule(), o2.getModule())) return 0;
      String pluginId1 = o1.getRootElement().getPluginId();
      String pluginId2 = o2.getRootElement().getPluginId();
      if (pluginId1 == null && pluginId2 == null) {
        return o1.getFile().getName().compareTo(o2.getFile().getName());
      }
      if (pluginId1 == null) return 1;
      if (pluginId2 == null) return -1;
      return Comparing.compare(pluginId1, pluginId2);
    });

    return ContainerUtil.map(elements, new Function<DomFileElement<IdeaPlugin>, PluginDescriptorCandidate>() {

      private Module myLastModule = currentModule;

      @Override
      public PluginDescriptorCandidate fun(DomFileElement<IdeaPlugin> element) {
        final Module module = element.getModule();
        boolean startsNewGroup = !myLastModule.equals(module);
        myLastModule = module;
        return new PluginDescriptorCandidate(element, startsNewGroup);
      }
    });
  }

  private static int groupMatchLevel(@NotNull List<String> targetGroupPath, @NotNull List<String> groupPath) {
    for (int i = 0; i < Math.min(targetGroupPath.size(), groupPath.size()); i++) {
      if (!targetGroupPath.get(i).equals(groupPath.get(i))) {
        return i;
      }
    }
    return Math.min(targetGroupPath.size(), groupPath.size());
  }

  public static List<DomFileElement<IdeaPlugin>> findAppropriateIntelliJModule(String moduleName,
                                                                                List<DomFileElement<IdeaPlugin>> elements) {
    String extensionsFile = INTELLIJ_MODULES.get(moduleName);
    if (extensionsFile != null) {
      for (DomFileElement<IdeaPlugin> element : elements) {
        if (element.getFile().getName().equals(extensionsFile)) {
          return Collections.singletonList(element);
        }
      }
    }
    return elements;
  }


  private static class PluginDescriptorCandidate {
    private final DomFileElement<IdeaPlugin> myDomFileElement;
    private final boolean myStartsNewGroup;

    private PluginDescriptorCandidate(DomFileElement<IdeaPlugin> domFileElement,
                                      boolean startsNewGroup) {
      myDomFileElement = domFileElement;
      myStartsNewGroup = startsNewGroup;
    }

    public String getText() {
      final String name = myDomFileElement.getFile().getName();
      final String pluginId = getPluginId();
      return pluginId != null ? name + " [" + pluginId + "]" : name;
    }

    public Icon getIcon() {
      return getPluginId() != null ? AllIcons.Nodes.Plugin : EmptyIcon.create(AllIcons.Nodes.Plugin);
    }

    public String getSeparatorText() {
      if (!myStartsNewGroup) return null;

      final Module module = myDomFileElement.getModule();
      return module == null ? null : module.getName();
    }

    private String getPluginId() {
      return myDomFileElement.getRootElement().getPluginId();
    }
  }
}
