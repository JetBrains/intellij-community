// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions;

import com.intellij.CommonBundle;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.source.JvmDeclarationSearch;
import com.intellij.lang.jvm.util.JvmClassUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serialization.MutableAccessor;
import com.intellij.serialization.SerializationException;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ShowSerializedXmlAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(ShowSerializedXmlAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null &&
                                   e.getData(CommonDataKeys.PSI_FILE) != null &&
                                   e.getData(CommonDataKeys.EDITOR) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (project == null || editor == null || psiFile == null) return;

    Pair<VirtualFile, String> fileAndClassName = getJvmFileAndClassName(editor, psiFile);
    if (fileAndClassName == null) {
      IdeUiService.getInstance().showErrorHint(editor, DevKitBundle.message("action.ShowSerializedXml.message.caret.must.be.at.class.identifier"));
      return;
    }

    VirtualFile virtualFile = fileAndClassName.first;
    String className = fileAndClassName.second;
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) {
      IdeUiService.getInstance().showErrorHint(editor, DevKitBundle.message("action.ShowSerializedXml.message.caret.must.be.at.class.identifier"));
      return;
    }

    CompilerManager.getInstance(project).make(new FileSetCompileScope(Collections.singletonList(virtualFile), new Module[]{module}), new CompileStatusNotification() {
      @Override
      public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
        if (aborted || errors > 0) return;
        generateAndShowXml(module, className);
      }
    });
  }

  private static void generateAndShowXml(final Module module, final String className) {
    final List<Path> files = new ArrayList<>();
    final List<String> list = OrderEnumerator.orderEntries(module).recursively().runtimeOnly().getPathsList().getPathList();
    for (String path : list) {
      files.add(Paths.get(path));
    }

    final Project project = module.getProject();
    PathClassLoader loader = new PathClassLoader(UrlClassLoader.build().files(files).parent(XmlSerializer.class.getClassLoader()));
    final Class<?> aClass;
    try {
      aClass = loader.loadClass(className);
    }
    catch (ClassNotFoundException e) {
      Messages.showErrorDialog(project, DevKitBundle.message("action.ShowSerializedXml.message.cannot.find.class", className),
                               CommonBundle.getErrorTitle());
      LOG.info(e);
      return;
    }

    final Object o;
    try {
      o = new SampleObjectGenerator().createValue(aClass, FList.emptyList());
    }
    catch (Exception e) {
      Messages.showErrorDialog(project,
                               DevKitBundle.message("action.ShowSerializedXml.message.cannot.generate.class", className, e.getMessage()),
                               CommonBundle.getErrorTitle());
      LOG.info(e);
      return;
    }

    final Element element;
    try {
      element = XmlSerializer.serialize(o);
    }
    catch (SerializationException e) {
      LOG.info(e);
      Throwable cause = e.getCause();
      Messages.showErrorDialog(project, e.getMessage() + (cause != null ? ": " + cause.getMessage() : ""), CommonBundle.getErrorTitle());
      return;
    }
    final @NlsSafe String text = JDOMUtil.writeElement(element);
    Messages.showIdeaMessageDialog(project, text, DevKitBundle.message("action.ShowSerializedXml.dialog.title", className),
                                   new String[]{CommonBundle.getOkButtonText()}, 0, Messages.getInformationIcon(), null);
  }

  private static @Nullable Pair<@NotNull VirtualFile, @NotNull String> getJvmFileAndClassName(Editor editor, PsiFile psiFile) {
    final PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;

    JvmClass jvmClass = ContainerUtil.findInstance(JvmDeclarationSearch.getElementsByIdentifier(element), JvmClass.class);
    if (jvmClass == null) return null;

    VirtualFile virtualFile = psiFile.getVirtualFile();
    String className = JvmClassUtil.getJvmClassName(jvmClass);
    if (virtualFile == null || className == null) return null;
    return new Pair<>(virtualFile, className);
  }

  private static class SampleObjectGenerator {
    private int myNum;

    public @Nullable Object createValue(Type type, final FList<Type> types) throws Exception {
      return createValue(type, types, Collections.emptyList());
    }

    public @Nullable Object createValue(Type type, final FList<Type> types, List<Type> elementTypes) throws Exception {
      if (types.contains(type)) return null;
      FList<Type> processedTypes = types.prepend(type);
      final Class<?> valueClass = type instanceof Class ? (Class<Object>)type : (Class<Object>)((ParameterizedType)type).getRawType();
      if (String.class.isAssignableFrom(valueClass)) {
        return "value" + (myNum++);
      }
      else if (byte.class.isAssignableFrom(valueClass) || Byte.class.isAssignableFrom(valueClass)
               || short.class.isAssignableFrom(valueClass) || Short.class.isAssignableFrom(valueClass)
               || int.class.isAssignableFrom(valueClass) || Integer.class.isAssignableFrom(valueClass)
               || long.class.isAssignableFrom(valueClass) || Long.class.isAssignableFrom(valueClass)) {
        return myNum++ % 127;
      }
      else if (double.class.isAssignableFrom(valueClass) || Double.class.isAssignableFrom(valueClass)
               || float.class.isAssignableFrom(valueClass) || Float.class.isAssignableFrom(valueClass)) {
        return 0.5 + myNum++;
      }
      else if (boolean.class.isAssignableFrom(valueClass) || Boolean.class.isAssignableFrom(valueClass)) {
        return (myNum++ % 2) == 0;
      }
      else if (valueClass.isEnum()) {
        final Object[] constants = valueClass.getEnumConstants();
        return constants[(myNum++) % constants.length];
      }
      else if (Collection.class.isAssignableFrom(valueClass) && type instanceof ParameterizedType) {
        return createCollection(valueClass, (ParameterizedType)type, processedTypes, elementTypes);
      }
      else if (Map.class.isAssignableFrom(valueClass) && type instanceof ParameterizedType) {
        return createMap((ParameterizedType)type, processedTypes);
      }
      else if (valueClass.isArray()) {
        return createArray(valueClass, processedTypes, elementTypes);
      }
      else if (Element.class.isAssignableFrom(valueClass)) {
        return new Element("customElement" + (myNum++)).setAttribute("attribute", "value" + (myNum++)).addContent(new Element("child" + (myNum++)));
      }
      else {
        return createObject(valueClass, processedTypes);
      }
    }

    public @NotNull Object createObject(@NotNull Class<?> aClass, FList<Type> processedTypes) throws Exception {
      Object o = ReflectionUtil.newInstance(aClass);
      for (MutableAccessor accessor : XmlSerializerUtil.getAccessors(aClass)) {
        AbstractCollection abstractCollection = accessor.getAnnotation(AbstractCollection.class);
        List<Type> elementTypes = abstractCollection != null ? Arrays.asList(abstractCollection.elementTypes()) : Collections.emptyList();
        Object value = createValue(accessor.getGenericType(), processedTypes, elementTypes);
        if (value != null) {
          accessor.set(o, value);
        }
      }
      return o;
    }

    private @NotNull Object createArray(Class<?> valueClass, FList<Type> processedTypes, List<Type> elementTypes) throws Exception {
      final Object[] array = (Object[])Array.newInstance(valueClass.getComponentType(), Math.max(elementTypes.size(), 2));
      for (int i = 0; i < array.length; i++) {
        Type type = elementTypes.isEmpty() ? valueClass.getComponentType() : elementTypes.get(i % elementTypes.size());
        array[i] = createValue(type, processedTypes);
      }
      return array;
    }

    private Object createMap(ParameterizedType type, FList<Type> processedTypes) throws Exception {
      Type keyType = type.getActualTypeArguments()[0];
      Type valueType = type.getActualTypeArguments()[1];
      final HashMap<Object, Object> map = new HashMap<>();
      for (int i = 0; i < 2; i++) {
        Object key = createValue(keyType, processedTypes);
        Object value = createValue(valueType, processedTypes);
        if (key != null && value != null) {
          map.put(key, value);
        }
      }
      return map;
    }

    private @Nullable Object createCollection(Class<?> aClass, ParameterizedType genericType, FList<Type> processedTypes, List<Type> elementTypes) throws Exception {
      Collection<Object> o;
      if (List.class.isAssignableFrom(aClass)) {
        o = new ArrayList<>();
      }
      else if (Set.class.isAssignableFrom(aClass)) {
        o = new HashSet<>();
      }
      else {
        return null;
      }
      final Type elementClass = genericType.getActualTypeArguments()[0];
      for (int i = 0; i < Math.max(elementTypes.size(), 2); i++) {
        Type type = elementTypes.isEmpty() ? elementClass : elementTypes.get(i % elementTypes.size());
        final Object item = createValue(type, processedTypes);
        if (item != null) {
          o.add(item);
        }
      }
      return o;
    }
  }
}
