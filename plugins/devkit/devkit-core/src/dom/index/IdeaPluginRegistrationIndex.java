// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ObjectUtils;
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
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.index.RegistrationEntry.RegistrationType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Class FQN or ID -> entry in {@code plugin.xml}.
 * <p>
 * <ul>
 *   <li>Application/Project/Module-component class - {@link Component#getInterfaceClass()} / {@link Component#getImplementationClass()} / {@link Component#getHeadlessImplementationClass()}</li>
 *   <li>Action/ActionGroup class - {@link Action#getClazz()}/{@link Group#getClazz()}</li>
 *   <li>Action/ActionGroup ID - {@link ActionOrGroup#getId()}</li>
 *   <li>Application/Project Listener class - {@link Listeners.Listener#getListenerClassName()}</li>
 *   <li>Listener topic class - {@link Listeners.Listener#getTopicClassName()}</li>
 * </ul>
 */
public class IdeaPluginRegistrationIndex extends PluginXmlIndexBase<String, List<RegistrationEntry>> {

  private static final int INDEX_VERSION = 8;

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

  public static boolean isRegisteredClass(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, null);
  }

  public static boolean isRegisteredComponentInterface(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, RegistrationType.COMPONENT_INTERFACE);
  }

  public static boolean isRegisteredApplicationComponent(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, RegistrationType.APPLICATION_COMPONENT);
  }

  public static boolean isRegisteredProjectComponent(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, RegistrationType.PROJECT_COMPONENT);
  }

  public static boolean isRegisteredModuleComponent(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, RegistrationType.MODULE_COMPONENT);
  }

  public static boolean isRegisteredActionOrGroup(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, RegistrationType.ACTION);
  }

  public static boolean isRegisteredListenerTopic(PsiClass psiClass, GlobalSearchScope scope) {
    return isRegisteredClass(psiClass, scope, RegistrationType.LISTENER_TOPIC);
  }

  public static boolean processComponent(Project project,
                                         PsiClass componentInterfaceOrImplementationClass,
                                         GlobalSearchScope scope,
                                         Processor<? super Component> processor) {
    String key = componentInterfaceOrImplementationClass.getQualifiedName();
    assert key != null : componentInterfaceOrImplementationClass;

    return processAll(project, key, scope,
                      EnumSet.of(RegistrationType.APPLICATION_COMPONENT,
                                 RegistrationType.PROJECT_COMPONENT,
                                 RegistrationType.MODULE_COMPONENT,
                                 RegistrationType.COMPONENT_INTERFACE),
                      Component.class,
                      true,
                      processor);
  }

  public static boolean processListener(@NotNull Project project,
                                        PsiClass listenerClass,
                                        GlobalSearchScope scope,
                                        Processor<? super Listeners.Listener> processor) {
    return doProcessListener(project, listenerClass, scope,
                             EnumSet.of(RegistrationType.APPLICATION_LISTENER, RegistrationType.PROJECT_LISTENER),
                             processor);
  }

  public static boolean processListenerTopic(@NotNull Project project,
                                             PsiClass topicClass,
                                             GlobalSearchScope scope,
                                             Processor<? super Listeners.Listener> processor) {
    return doProcessListener(project, topicClass, scope, EnumSet.of(RegistrationType.LISTENER_TOPIC), processor);
  }

  private static boolean doProcessListener(@NotNull Project project,
                                           PsiClass psiClass,
                                           GlobalSearchScope scope,
                                           EnumSet<RegistrationType> types,
                                           Processor<? super Listeners.Listener> processor) {
    final String key = psiClass.getQualifiedName();
    assert key != null : psiClass;

    return processAll(project, key, scope,
                      types,
                      Listeners.Listener.class,
                      true,
                      processor);
  }

  /**
   * @param type {@code null} for any
   */
  private static boolean isRegisteredClass(PsiClass psiClass, GlobalSearchScope scope, @Nullable RegistrationType type) {
    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }

    return !FileBasedIndex.getInstance()
      .processValues(NAME, qualifiedName, null,
                     (file, value) -> ContainerUtil.process(value, entry -> {
                       RegistrationType registrationType = entry.getRegistrationType();
                       if (type == null) {
                         return !(registrationType.isClass());
                       }

                       return !(registrationType == type);
                     }),
                     scope);
  }

  public static boolean processAllActionOrGroup(@NotNull Project project,
                                                GlobalSearchScope scope,
                                                Processor<? super ActionOrGroup> processor) {
    Set<String> keys = new HashSet<>();
    FileBasedIndex.getInstance().processAllKeys(NAME, s -> keys.add(s), scope, null);
    return ContainerUtil.process(keys, s -> processActionOrGroup(project, s, scope, processor));
  }

  public static boolean processActionOrGroupClass(@NotNull Project project,
                                                  PsiClass actionOrGroupClass,
                                                  GlobalSearchScope scope,
                                                  Processor<? super ActionOrGroup> processor) {
    String fqn = actionOrGroupClass.getQualifiedName();
    assert fqn != null : actionOrGroupClass;

    return doProcessActionOrGroup(project, fqn, scope, EnumSet.of(RegistrationType.ACTION), processor);
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
                                                @NotNull String key,
                                                GlobalSearchScope scope,
                                                EnumSet<RegistrationType> types,
                                                Processor<? super ActionOrGroup> processor) {
    return processAll(project, key, scope, types, ActionOrGroup.class, false, processor);
  }

  /**
   * @param domClazz     Expected DOM class, possibly parent of the indexed DOM element (see {@code useParentDom})
   * @param useParentDom {@code true} if DOM element to process is parent of the indexed DOM element
   * @see RegistrationIndexer
   */
  private static <T extends DomElement> boolean processAll(@NotNull Project project,
                                                           @NotNull String key,
                                                           GlobalSearchScope scope,
                                                           EnumSet<RegistrationType> types,
                                                           Class<T> domClazz,
                                                           boolean useParentDom,
                                                           Processor<? super T> processor) {
    List<XmlTag> tags = collectTags(project, key, scope, types);

    return ContainerUtil.process(tags, tag -> {
      final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);

      T t;
      if (useParentDom) {
        t = DomUtil.getParentOfType(domElement, domClazz, false);
      }
      else {
        t = ObjectUtils.tryCast(domElement, domClazz);
      }
      if (t == null) return true;

      return processor.process((T)t);
    });
  }

  @NotNull
  private static List<XmlTag> collectTags(@NotNull Project project,
                                          @NotNull String key,
                                          GlobalSearchScope scope,
                                          EnumSet<RegistrationType> types) {
    List<XmlTag> tags = new SmartList<>();
    FileBasedIndex.getInstance().processValues(NAME, key, null, (file, value) -> {
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
    return tags;
  }
}
