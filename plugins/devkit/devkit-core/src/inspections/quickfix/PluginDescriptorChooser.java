// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
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
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PluginDescriptorChooser {

  private static final Map<String, String> INTELLIJ_MODULES =
    Map.ofEntries(
      Map.entry("intellij.platform.ide", "PlatformExtensions.xml"),
      Map.entry("intellij.platform.ide.impl", "PlatformExtensions.xml"),
      Map.entry("intellij.platform.lang", "LangExtensions.xml"),
      Map.entry("intellij.platform.execution.impl", "LangExtensions.xml"),
      Map.entry("intellij.platform.lang.impl", "LangExtensions.xml"),
      Map.entry("intellij.platform.vcs", "VcsExtensions.xml"),
      Map.entry("intellij.platform.vcs.impl", "VcsExtensions.xml"),
      Map.entry("intellij.java", "IdeaPlugin.xml"),
      Map.entry("intellij.java.impl", "IdeaPlugin.xml"),
      Map.entry("intellij.java.impl.inspections", "IdeaPlugin.xml"),
      Map.entry("intellij.java.analysis.impl", "IdeaPlugin.xml"));

  public static void show(final Project project,
                          final Editor editor,
                          final PsiFile file,
                          final Consumer<? super DomFileElement<IdeaPlugin>> consumer) {
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
      HintManager.getInstance().showErrorHint(editor, DevKitBundle.message("plugin.descriptor.chooser.cannot.find"));
      return;
    }

    if (elements.size() == 1) {
      consumer.consume(elements.get(0));
      return;
    }

    final BaseListPopupStep<PluginDescriptorCandidate> popupStep =
      new BaseListPopupStep<>(DevKitBundle.message("plugin.descriptor.chooser.popup.title"), createCandidates(module, elements)) {

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public Icon getIconFor(PluginDescriptorCandidate candidate) {
          return candidate.getIcon();
        }

        @Override
        public @NotNull String getTextFor(PluginDescriptorCandidate candidate) {
          return candidate.getText();
        }

        @Override
        public @Nullable ListSeparator getSeparatorAbove(PluginDescriptorCandidate candidate) {
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

  public static @NotNull Extensions findOrCreateExtensionsForEP(DomFileElement<? extends IdeaPlugin> domFileElement, String epName) {
    final IdeaPlugin ideaPlugin = domFileElement.getRootElement();
    for (Extensions extensions : ideaPlugin.getExtensions()) {
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
                                                                  List<? extends DomFileElement<IdeaPlugin>> elements) {
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
        int groupComparison = Integer.compare(groupMatchLevel(groupPath, grouper.getGroupPath(module2)),
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

  public static List<DomFileElement<IdeaPlugin>> findAppropriateIntelliJModule(@NotNull String moduleName,
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


  private static final class PluginDescriptorCandidate {
    private final DomFileElement<IdeaPlugin> myDomFileElement;
    private final boolean myStartsNewGroup;

    private PluginDescriptorCandidate(DomFileElement<IdeaPlugin> domFileElement,
                                      boolean startsNewGroup) {
      myDomFileElement = domFileElement;
      myStartsNewGroup = startsNewGroup;
    }

    public @NlsContexts.ListItem String getText() {
      final String name = myDomFileElement.getFile().getName();
      final String pluginId = getPluginId();
      return pluginId != null ? name + " [" + pluginId + "]" : name;
    }

    public Icon getIcon() {
      return getPluginId() != null ? AllIcons.Nodes.Plugin : EmptyIcon.create(AllIcons.Nodes.Plugin);
    }

    public @NlsContexts.Separator String getSeparatorText() {
      if (!myStartsNewGroup) return null;

      final Module module = myDomFileElement.getModule();
      return module == null ? null : module.getName();
    }

    private @NlsSafe String getPluginId() {
      return myDomFileElement.getRootElement().getPluginId();
    }
  }
}
