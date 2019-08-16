// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.PluginModule;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PluginIdModuleIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("PluginIdModuleIndex");

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return inputData -> {
      IdeaPlugin plugin = RegistrationIndexer.obtainIdeaPlugin(inputData);
      if (plugin == null) return Collections.emptyMap();

      List<String> ids = new ArrayList<>();
      ids.add(StringUtil.notNullize(plugin.getPluginId()));
      for (PluginModule module : plugin.getModules()) {
        ContainerUtil.addIfNotNull(ids, module.getValue().getStringValue());
      }
      return ContainerUtil.newHashMap(ids, Collections.nCopies(ids.size(), null));
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static List<IdeaPlugin> findPlugins(@NotNull DomElement place, @NotNull String idOrModule) {
    Project project = place.getManager().getProject();
    GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project).union(LibraryScopeCache.getInstance(project).getLibrariesOnlyScope());
    Collection<VirtualFile> vFiles = FileBasedIndex.getInstance().getContainingFiles(NAME, idOrModule, scope);
    return JBIterable.from(vFiles)
      .map(PsiManager.getInstance(project)::findFile)
      .filter(XmlFile.class)
      .map(DescriptorUtil::getIdeaPlugin)
      .filter(Condition.NOT_NULL)
      .map(fe -> fe.getRootElement())
      .toList();
  }
}
