// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
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
@SuppressWarnings("UnusedReturnValue")
public final class IdeaPluginRegistrationIndex extends PluginXmlIndexBase<String, List<RegistrationEntry>> {

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

  @Override
  public @NotNull ID<String, List<RegistrationEntry>> getName() {
    return NAME;
  }

  @Override
  protected Map<String, List<RegistrationEntry>> performIndexing(IdeaPlugin plugin) {
    return new RegistrationIndexer(plugin).indexFile();
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<List<RegistrationEntry>> getValueExternalizer() {
    return myValueExternalizer;
  }

  @Override
  public int getVersion() {
    return BASE_INDEX_VERSION + INDEX_VERSION;
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
                                         Processor<? extends Component> processor) {
    return processAll(project, componentInterfaceOrImplementationClass, scope,
                      EnumSet.of(RegistrationType.APPLICATION_COMPONENT,
                                 RegistrationType.PROJECT_COMPONENT,
                                 RegistrationType.MODULE_COMPONENT,
                                 RegistrationType.COMPONENT_INTERFACE),
                      processor);
  }

  public static boolean processListener(@NotNull Project project,
                                        PsiClass listenerClass,
                                        GlobalSearchScope scope,
                                        Processor<? extends Listeners.Listener> processor) {
    return processAll(project, listenerClass, scope,
                      EnumSet.of(RegistrationType.APPLICATION_LISTENER, RegistrationType.PROJECT_LISTENER),
                      processor);
  }

  public static boolean processListenerTopic(@NotNull Project project,
                                             PsiClass topicClass,
                                             GlobalSearchScope scope,
                                             Processor<? extends Listeners.Listener> processor) {
    return processAll(project, topicClass, scope, EnumSet.of(RegistrationType.LISTENER_TOPIC), processor);
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
                                                Processor<? extends ActionOrGroup> processor) {
    Set<String> keys = new HashSet<>();
    FileBasedIndex.getInstance().processAllKeys(NAME, s -> keys.add(s), scope, null);
    return ContainerUtil.process(keys, s -> processActionOrGroup(project, s, scope, processor));
  }

  public static boolean processActionOrGroupClass(@NotNull Project project,
                                                  PsiClass actionOrGroupClass,
                                                  GlobalSearchScope scope,
                                                  Processor<? extends ActionOrGroup> processor) {
    return processAll(project, actionOrGroupClass, scope, EnumSet.of(RegistrationType.ACTION), processor);
  }

  public static boolean processActionOrGroup(@NotNull Project project,
                                             @NotNull String actionOrGroupId,
                                             GlobalSearchScope scope,
                                             Processor<? extends ActionOrGroup> processor) {
    return processAll(project, actionOrGroupId, scope, EnumSet.of(RegistrationType.ACTION_ID, RegistrationType.ACTION_GROUP_ID), processor);
  }

  public static boolean processAction(@NotNull Project project,
                                      @NotNull String actionId,
                                      GlobalSearchScope scope,
                                      Processor<? extends ActionOrGroup> processor) {
    return processAll(project, actionId, scope, EnumSet.of(RegistrationType.ACTION_ID), processor);
  }

  public static boolean processGroup(@NotNull Project project,
                                     @NotNull String actionGroupId,
                                     GlobalSearchScope scope,
                                     Processor<? extends ActionOrGroup> processor) {
    return processAll(project, actionGroupId, scope, EnumSet.of(RegistrationType.ACTION_GROUP_ID), processor);
  }

  private static <T extends DomElement> boolean processAll(@NotNull Project project,
                                                           @NotNull PsiClass psiClass,
                                                           GlobalSearchScope scope,
                                                           EnumSet<RegistrationType> types,
                                                           Processor<T> processor) {
    String qualifiedName = psiClass.getQualifiedName();
    assert qualifiedName != null;

    return processAll(project, qualifiedName, scope, types, processor);
  }

  /**
   * @see RegistrationIndexer
   */
  @SuppressWarnings("unchecked")
  private static <T extends DomElement> boolean processAll(@NotNull Project project,
                                                           @NotNull String key,
                                                           GlobalSearchScope scope,
                                                           EnumSet<RegistrationType> types,
                                                           Processor<T> processor) {
    RegistrationEntry.RegistrationDomType registrationDomType = ContainerUtil.getFirstItem(types).getRegistrationDomType();

    return processIndexEntries(project, key, scope, types, tag -> {
      final DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);

      T t;
      if (registrationDomType.isUseParentDom()) {
        t = (T)DomUtil.getParentOfType(domElement, registrationDomType.getDomClass(), false);
      }
      else {
        t = (T)ObjectUtils.tryCast(domElement, registrationDomType.getDomClass());
      }
      if (t == null) return true;

      return processor.process(t);
    });
  }

  private static boolean processIndexEntries(@NotNull Project project,
                                             @NotNull String key,
                                             GlobalSearchScope scope,
                                             EnumSet<RegistrationType> types,
                                             Processor<XmlTag> processor) {
    return FileBasedIndex.getInstance().processValues(NAME, key, null, (file, value) -> {
      for (RegistrationEntry entry : value) {
        if (!types.contains(entry.getRegistrationType())) {
          continue;
        }

        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (!(psiFile instanceof XmlFile)) continue;

        XmlTag xmlTag = AstLoadingFilter.forceAllowTreeLoading(psiFile, () -> {
          PsiElement psiElement = psiFile.findElementAt(entry.getOffset());
          return PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
        });
        if (!processor.process(xmlTag)) return false;
      }
      return true;
    }, scope);
  }
}
