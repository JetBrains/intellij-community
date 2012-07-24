/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions;

import com.intellij.CommonBundle;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.FList;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author nik
 */
public class ShowSerializedXmlAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.actions.ShowSerializedXmlAction");

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null && e.getData(LangDataKeys.PSI_FILE) != null
                                   && e.getData(PlatformDataKeys.EDITOR) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiClass psiClass = getPsiClass(e);
    if (psiClass == null) return;

    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module == null || virtualFile == null) return;

    final String className = ClassUtil.getJVMClassName(psiClass);

    final Project project = getEventProject(e);
    CompilerManager.getInstance(project).make(new FileSetCompileScope(Arrays.asList(virtualFile), new Module[]{module}), new CompileStatusNotification() {
      @Override
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (aborted || errors > 0) return;
        generateAndShowXml(module, className);
      }
    });
  }

  private static void generateAndShowXml(final Module module, final String className) {
    final List<URL> urls = new ArrayList<URL>();
    final List<String> list = OrderEnumerator.orderEntries(module).recursively().runtimeOnly().getPathsList().getPathList();
    for (String path : list) {
      try {
        urls.add(new File(FileUtil.toSystemIndependentName(path)).toURI().toURL());
      }
      catch (MalformedURLException e1) {
        LOG.info(e1);
      }
    }
    
    final Project project = module.getProject();
    UrlClassLoader loader = new UrlClassLoader(urls, XmlSerializer.class.getClassLoader());
    final Class<?> aClass;
    try {
      aClass = Class.forName(className, true, loader);
    }
    catch (ClassNotFoundException e) {
      Messages.showErrorDialog(project, "Cannot find class '" + className + "'", CommonBundle.getErrorTitle());
      LOG.info(e);
      return;
    }

    final Object o;
    try {
      o = new SampleObjectGenerator().createValue(aClass, FList.<Type>emptyList());
    }
    catch (Exception e) {
      Messages.showErrorDialog(project, "Cannot generate class '" + className + "': " + e.getMessage(), CommonBundle.getErrorTitle());
      LOG.info(e);
      return;
    }

    final Element element = XmlSerializer.serialize(o);
    final String text = JDOMUtil.writeElement(element, "\n");
    Messages.showInfoMessage(project, text, "Serialized XML for '" + className + "'");
  }

  @Nullable
  private static PsiClass getPsiClass(AnActionEvent e) {
    final PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor == null || psiFile == null) return null;
    final PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }

  private static class SampleObjectGenerator {
    private int myNum;

    @Nullable
    public Object createValue(Type type, final FList<Type> types) throws Exception {
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
        return createCollection(valueClass, (ParameterizedType)type, processedTypes);
      }
      else if (Map.class.isAssignableFrom(valueClass) && type instanceof ParameterizedType) {
        return createMap((ParameterizedType)type, processedTypes);
      }
      else if (valueClass.isArray()) {
        return createArray(valueClass, processedTypes);
      }
      else if (Element.class.isAssignableFrom(valueClass)) {
        return new Element("customElement" + (myNum++)).setAttribute("attribute", "value" + (myNum++)).addContent(new Element("child" + (myNum++)));
      }
      else {
        return createObject(valueClass, processedTypes);
      }
    }

    public Object createObject(Class<?> aClass, FList<Type> processedTypes) throws Exception {
      final Object o = aClass.newInstance();
      for (Accessor accessor : XmlSerializerUtil.getAccessors(aClass)) {
        final Type type = accessor.getGenericType();
        Object value = createValue(type, processedTypes);
        if (value != null) {
          accessor.write(o, value);
        }
      }
      return o;
    }

    @Nullable
    private Object createArray(Class<?> valueClass, FList<Type> processedTypes) throws Exception {
      final Object[] array = (Object[])Array.newInstance(valueClass.getComponentType(), 2);
      for (int i = 0; i < array.length; i++) {
        array[i] = createValue(valueClass.getComponentType(), processedTypes);
      }
      return array;
    }

    private Object createMap(ParameterizedType type, FList<Type> processedTypes) throws Exception {
      Type keyType = type.getActualTypeArguments()[0];
      Type valueType = type.getActualTypeArguments()[0];
      final HashMap<Object, Object> map = new HashMap<Object, Object>();
      for (int i = 0; i < 2; i++) {
        Object key = createValue(keyType, processedTypes);
        Object value = createValue(valueType, processedTypes);
        if (key != null && value != null) {
          map.put(key, value);
        }
      }
      return map;
    }

    @Nullable
    private Object createCollection(Class<?> aClass, ParameterizedType genericType, FList<Type> processedTypes) throws Exception {
      final Type elementClass = genericType.getActualTypeArguments()[0];
      Collection<Object> o;
      if (List.class.isAssignableFrom(aClass)) {
        o = new ArrayList<Object>();
      }
      else if (Set.class.isAssignableFrom(aClass)) {
        o = new HashSet<Object>();
      }
      else {
        return null;
      }
      for (int i = 0; i < 2; i++) {
        final Object item = createValue(elementClass, processedTypes);
        if (item != null) {
          o.add(item);
        }
      }
      return o;
    }
  }
}
