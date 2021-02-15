/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.index.RegistrationEntry.RegistrationType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class IdeaPluginRegistrationIndex extends PluginXmlIndexBase<String, List<RegistrationEntry>> {

  private static final int INDEX_VERSION = 3;

  private static final ID<String, List<RegistrationEntry>> NAME = ID.create("IdeaPluginRegistrationIndex");

  private final DataExternalizer<List<RegistrationEntry>> myValueExternalizer = new DataExternalizer<>() {

    @Override
    public void save(@NotNull DataOutput out, List<RegistrationEntry> values) throws IOException {
      DataInputOutputUtilRt.writeSeq(out, values, entry -> {
        DataInputOutputUtil.writeINT(out, entry.getRegistrationType().ordinal());
        DataInputOutputUtil.writeINT(out, entry.getOffset());
      });
    }

    @Override
    public List<RegistrationEntry> read(@NotNull DataInput in) throws IOException {
      return DataInputOutputUtilRt.readSeq(in, () -> {
        RegistrationType type = RegistrationType.values()[DataInputOutputUtil.readINT(in)];
        int offset = DataInputOutputUtil.readINT(in);
        return new RegistrationEntry(type, offset);
      });
    }
  };

  @NotNull
  @Override
  public ID<String, List<RegistrationEntry>> getName() {
    return NAME;
  }

  @Override
  protected Map<String, List<RegistrationEntry>> performIndexing(IdeaPlugin plugin) {
    return new RegistrationIndexer(plugin).indexFile();
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<List<RegistrationEntry>> getValueExternalizer() {
    return myValueExternalizer;
  }

  @Override
  public int getVersion() {
    return INDEX_VERSION;
  }

  public static boolean isRegisteredApplicationComponent(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegistered(psiClass, scope, RegistrationType.APPLICATION_COMPONENT);
  }

  public static boolean isRegisteredProjectComponent(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegistered(psiClass, scope, RegistrationType.PROJECT_COMPONENT);
  }

  public static boolean isRegisteredModuleComponent(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegistered(psiClass, scope, RegistrationType.MODULE_COMPONENT);
  }

  public static boolean isRegisteredAction(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegistered(psiClass, scope, RegistrationType.ACTION);
  }

  private static boolean isRegistered(PsiClass psiClass, GlobalSearchScope scope, RegistrationType type) {
    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }

    return !FileBasedIndex.getInstance()
      .processValues(NAME, qualifiedName, null,
                     (file, value) -> ContainerUtil.process(value, entry -> !(entry.getRegistrationType() == type)),
                     scope);
  }

  public static boolean processAllActionOrGroup(@NotNull Project project,
                                                GlobalSearchScope scope,
                                                Processor<? super ActionOrGroup> processor) {
    Set<String> keys = new HashSet<>();
    FileBasedIndex.getInstance().processAllKeys(NAME, s -> keys.add(s), scope, null);
    return ContainerUtil.process(keys, s -> processActionOrGroup(project, s, scope, processor));
  }

  public static boolean processActionOrGroup(@NotNull Project project,
                                             @NotNull String actionOrGroupId,
                                             GlobalSearchScope scope,
                                             Processor<? super ActionOrGroup> processor) {
    return doProcessActionOrGroup(project, actionOrGroupId, scope,
                                  EnumSet.of(RegistrationType.ACTION_ID, RegistrationType.ACTION_GROUP_ID),
                                  processor);
  }

  public static boolean processAction(@NotNull Project project,
                                      @NotNull String actionId,
                                      GlobalSearchScope scope,
                                      Processor<? super ActionOrGroup> processor) {
    return doProcessActionOrGroup(project, actionId, scope, EnumSet.of(RegistrationType.ACTION_ID), processor);
  }

  public static boolean processGroup(@NotNull Project project,
                                     @NotNull String actionGroupId,
                                     GlobalSearchScope scope,
                                     Processor<? super ActionOrGroup> processor) {
    return doProcessActionOrGroup(project, actionGroupId, scope, EnumSet.of(RegistrationType.ACTION_GROUP_ID), processor);
  }

  private static boolean doProcessActionOrGroup(@NotNull Project project,
                                                @NotNull String actionOrGroupId,
                                                GlobalSearchScope scope,
                                                EnumSet<RegistrationType> types,
                                                Processor<? super ActionOrGroup> processor) {
    List<XmlTag> tags = new SmartList<>();
    FileBasedIndex.getInstance().processValues(NAME, actionOrGroupId, null, (file, value) -> {
      for (RegistrationEntry entry : value) {
        if (!types.contains(entry.getRegistrationType())) {
          continue;
        }

        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (!(psiFile instanceof XmlFile)) continue;

        PsiElement psiElement = psiFile.findElementAt(entry.getOffset());
        XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
        tags.add(xmlTag);
      }
      return true;
    }, scope);

    return ContainerUtil.process(tags, tag -> {
      final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);

      if (!(domElement instanceof ActionOrGroup)) return true;
      return processor.process((ActionOrGroup)domElement);
    });
  }
}
