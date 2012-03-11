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
package org.jetbrains.plugins.groovy.swingBuilder;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class SwingBuilderNonCodeMemberContributor extends NonCodeMembersContributor {

  private static final Key<MultiMap<String, PsiMethod>> KEY = Key.create("SwingBuilderNonCodeMemberContributor.KEY");

  private static class MyBuilder {
    private final PsiManager myManager;
    private final MultiMap<String, PsiMethod> myResult = new MultiMap<String, PsiMethod>();
    private final GlobalSearchScope myResolveScope;
    private final PsiElementFactory myFactory;
    private final PsiClass mySwingBuilderClass;

    private final Map<String, PsiType> myTypeMap = new HashMap<String, PsiType>();

    private MyBuilder(PsiClass swingBuilderClass) {
      myManager = swingBuilderClass.getManager();
      mySwingBuilderClass = swingBuilderClass;
      myResolveScope = swingBuilderClass.getResolveScope();
      myFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
    }

    public class MyMethodBuilder extends GrLightMethodBuilder {
      private String myNavigationClass;

      public MyMethodBuilder(PsiManager manager, String name) {
        super(manager, name);
      }

      @NotNull
      @Override
      public PsiElement getNavigationElement() {
        PsiElement res = super.getNavigationElement();
        if (res != this || myNavigationClass == null) return res;

        PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(myNavigationClass, getResolveScope());
        if (aClass == null) return res;

        PsiMethod[] methods = aClass.findMethodsByName("newInstance", false);
        if (methods.length != 1) return aClass;

        return methods[0];
      }

      @Override
      public MyMethodBuilder addParameter(@NotNull String name, @NotNull String type, boolean isOptional) {
        return (MyMethodBuilder)addParameter(name, type(type), isOptional);
      }

      public MyMethodBuilder addClosureParam() {
        addParameter("closure", GroovyCommonClassNames.GROOVY_LANG_CLOSURE, true);
        return this;
      }

      public MyMethodBuilder setNavigationClass(String navigationClass) {
        myNavigationClass = navigationClass;
        return this;
      }
    }

    @NotNull
    private PsiType type(@NotNull String typeName) {
      PsiType res = myTypeMap.get(typeName);
      if (res == null) {
        res = myFactory.createTypeByFQClassName(typeName, myResolveScope);
        myTypeMap.put(typeName, res);
      }
      return res;
    }

    private void add(@NotNull PsiMethod method) {
      myResult.putValue(method.getName(), method);
    }

    private MyMethodBuilder method(String name, String returnType) {
      MyMethodBuilder res = new MyMethodBuilder(myManager, name);
      res.setModifiers(GrModifierFlags.PUBLIC_MASK);
      res.setReturnType(type(returnType));
      res.setContainingClass(mySwingBuilderClass);
      return res;
    }

    private MyMethodBuilder methodWithAttr(String name, String returnType) {
      return method(name, returnType).addParameter("attr", CommonClassNames.JAVA_UTIL_MAP, true);
    }

    private MyMethodBuilder method(String name, String returnType, String paramName, String paramType, boolean isOptional) {
      MyMethodBuilder res = methodWithAttr(name, returnType);
      res.addParameter(paramName, paramType, isOptional);
      res.addClosureParam();

      return res;
    }

    private void beanFactory(String factoryName, String name, String returnType, boolean parameterOptional) {
      add(method(name, returnType, "value", CommonClassNames.JAVA_LANG_STRING, false).setNavigationClass(factoryName));
      add(method(name, returnType, "value", returnType, parameterOptional).setNavigationClass(factoryName));
    }

    private MyMethodBuilder acceptAllMethod(String name, String returnType, @Nullable String navigationClass) {
      return methodWithAttr(name, returnType)
        .addParameter("value", CommonClassNames.JAVA_LANG_OBJECT, true)
        .addClosureParam()
        .setNavigationClass(navigationClass);
    }

    private MyMethodBuilder acceptAllMethodLeaf(String name, String returnType, @Nullable String navigationClass) {
      return methodWithAttr(name, returnType)
        .addParameter("value", CommonClassNames.JAVA_LANG_OBJECT, true)
        .setNavigationClass(navigationClass);
    }

    private void registerExplicitMethod(String name, String realMethodName) {
      for (PsiMethod method : mySwingBuilderClass.findMethodsByName(realMethodName, false)) {
        add(GrLightMethodBuilder.wrap(method));
      }
    }

    private void generateMethods() {
      // registerSupportNodes()
      beanFactory("groovy.swing.factory.ActionFactory", "action", "javax.swing.Action", true);

      add(method("actions", CommonClassNames.JAVA_UTIL_LIST)
            .addClosureParam()
            .setNavigationClass("groovy.swing.factory.CollectionFactory"));

      add(methodWithAttr("map", CommonClassNames.JAVA_UTIL_MAP)
            .addClosureParam()
            .setNavigationClass("groovy.swing.factory.MapFactory"));


      add(acceptAllMethod("imageIcon", "javax.swing.ImageIcon", "groovy.swing.factory.ImageIconFactory")
            .setNamedParameters(ImmutableMap.<String, NamedArgumentDescriptor>builder()
                                  .put("image", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Image"), null))
                                  .put("url", new NamedArgumentDescriptor.TypeCondition(type("java.net.URL"), null))
                                  .put("file", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                                  .put("resource", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                                  .put("class", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                                  .put("description", NamedArgumentDescriptor.TYPE_STRING)
                                  .build()
            ));

      beanFactory("groovy.swing.factory.ButtonGroupFactory", "buttonGroup", "javax.swing.BoxLayout", true);

      add(method("noparent", CommonClassNames.JAVA_UTIL_LIST)
            .addClosureParam()
            .setNavigationClass("groovy.swing.factory.CollectionFactory"));

      registerExplicitMethod("keyStrokeAction", "createKeyStrokeAction");
      //registerExplicitMethod("shortcut", "shortcut");

      // registerBinding()
      add(acceptAllMethod("bind", "org.codehaus.groovy.binding.FullBinding", "groovy.swing.factory.BindFactory")
            .setNamedParameters(
              ImmutableMap.<String, NamedArgumentDescriptor>builder()
                .put("source", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .put("target", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .put("update", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .put("targetProperty", NamedArgumentDescriptor.TYPE_STRING)
                .put("mutual", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .put("sourceEvent", NamedArgumentDescriptor.TYPE_STRING)
                .put("sourceValue", NamedArgumentDescriptor.TYPE_CLOSURE)
                .put("sourceProperty", NamedArgumentDescriptor.TYPE_STRING)
                .put("value", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .put("group", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .build()
            ));

      add(acceptAllMethodLeaf("bindProxy", "org.codehaus.groovy.binding.BindingProxy", "groovy.swing.factory.BindProxyFactory")
            .setNamedParameters(
              ImmutableMap.<String, NamedArgumentDescriptor>builder()
                .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .build()
            ));

      add(acceptAllMethod("bindGroup", "org.codehaus.groovy.binding.AggregateBinding", "groovy.swing.factory.BindGroupFactory")
            .setNamedParameters(
              ImmutableMap.<String, NamedArgumentDescriptor>builder()
                .put("bind", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .build()
            ));

      // registerPassThruNodes()
      add(methodWithAttr("widget", "java.awt.Component").addParameter("component", "java.awt.Component", true)
            .setNamedParameters(
              ImmutableMap.<String, NamedArgumentDescriptor>builder()
                .put("widget", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Component"), null))
                .build()
            ));
      add(methodWithAttr("container", "java.awt.Component").addParameter("component", "java.awt.Component", true).addClosureParam()
            .setNamedParameters(
              ImmutableMap.<String, NamedArgumentDescriptor>builder()
                .put("container", new NamedArgumentDescriptor.TypeCondition(type("java.awt.Component"), null))
                .build()
            ));
      add(methodWithAttr("bean", CommonClassNames.JAVA_LANG_OBJECT).addParameter("bean", CommonClassNames.JAVA_LANG_OBJECT, true)
            .setNamedParameters(
              ImmutableMap.<String, NamedArgumentDescriptor>builder()
                .put("bean", NamedArgumentDescriptor.SIMPLE_ON_TOP)
                .build()
            ));

    }

  }

  //private static void ensureMapInitialized() {
  //  Map<String, FactoryDescriptor> res = new LinkedHashMap<String, FactoryDescriptor>();
  //
  //  FactoryDescriptor[] descriptors = new FactoryDescriptor[]{
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BevelBorderFactory", "loweredBevelBorder", "javax.swing.border.Border", CommonClassNames.JAVA_LANG_OBJECT),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BevelBorderFactory", "raisedBevelBorder", "javax.swing.border.Border", CommonClassNames.JAVA_LANG_OBJECT),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BindFactory", "bind", "org.codehaus.groovy.binding.BindingUpdatable", CommonClassNames.JAVA_LANG_STRING),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BindGroupFactory", "bindGroup", "org.codehaus.groovy.binding.AggregateBinding"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BindProxyFactory", "bindProxy", "org.codehaus.groovy.binding.BindingProxy", CommonClassNames.JAVA_LANG_OBJECT, false),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BoxFactory", "box", "javax.swing.Box", "javax.swing.Box"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.BoxLayoutFactory", "boxLayout", "javax.swing.BoxLayout"),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ButtonGroupFactory", "buttonGroup", "javax.swing.BoxLayout", true),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.CellEditorFactory", "cellEditor", "groovy.swing.impl.ClosureCellEditor"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.CellEditorGetValueFactory", "editorValue", CommonClassNames.JAVA_UTIL_MAP),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.CellEditorGetValueFactory", "editorValue", CommonClassNames.JAVA_UTIL_MAP),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.CellEditorPrepareFactory", "prepareEditor", CommonClassNames.JAVA_UTIL_MAP),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.ClosureColumnFactory", "closureColumn", "javax.swing.table.TableColumn"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.CollectionFactory", "noparent", "java.util.List"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.CollectionFactory", "actions", "java.util.List"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.ColumnFactory", "column", "javax.swing.table.TableColumn", CommonClassNames.JAVA_LANG_OBJECT),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.ColumnModelFactory", "columnModel", "javax.swing.table.TableColumnModel", "javax.swing.table.TableColumnModel"),
  //    new SimpleFactoryDescriptor("groovy.swing.factory.ComboBoxFactory", "comboBox", "javax.swing.JComboBox", "javax.swing.JComboBox"),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "tree", "javax.swing.JTree", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "toolBar", "javax.swing.JToolBar", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "menu", "javax.swing.JMenu", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "fileChooser", "javax.swing.JFileChooser", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "colorChooser", "javax.swing.JColorChooser", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "layeredPane", "javax.swing.JLayeredPane", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "progressBar", "javax.swing.JProgressBar", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "menuBar", "javax.swing.JMenuBar", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "viewport", "javax.swing.JViewport", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "desktopPane", "javax.swing.JDesktopPane", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "popupMenu", "javax.swing.JPopupMenu", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "spinner", "javax.swing.JSpinner", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "panel", "javax.swing.JPanel", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "slider", "javax.swing.JSlider", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "optionPane", "javax.swing.JOptionPane", true),
  //    new BeanFactoryDescriptor("groovy.swing.factory.ComponentFactory", "scrollBar", "javax.swing.JScrollBar", true),
  //  };
  //}

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) return;

    MultiMap<String, PsiMethod> methodMap = aClass.getUserData(KEY);
    if (methodMap == null) {
      MyBuilder builder = new MyBuilder(aClass);
      builder.generateMethods();
      methodMap = ((UserDataHolderEx)aClass).putUserDataIfAbsent(KEY, builder.myResult);
    }

    String nameHint = ResolveUtil.getNameHint(processor);

    Collection<? extends PsiMethod> methods = nameHint == null ? methodMap.values() : methodMap.get(nameHint);

    for (PsiMethod method : methods) {
      if (!processor.execute(method, state)) return;
    }
  }

  @Override
  protected String getParentClassName() {
    return "groovy.swing.SwingBuilder";
  }

}
