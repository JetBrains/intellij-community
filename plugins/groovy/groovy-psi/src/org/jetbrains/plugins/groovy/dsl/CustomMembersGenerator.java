// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.dsl.DescriptorsKt.*;

public class CustomMembersGenerator extends GroovyObjectSupport implements GdslMembersHolderConsumer {
  private static final GdslMembersProvider[] PROVIDERS = GdslMembersProvider.EP_NAME.getExtensions();
  public static final @NonNls String THROWS = "throws";
  private FList<@NotNull Descriptor> myDeclarations = FList.emptyList();
  private final List<CustomMembersHolder> myMemberHolders = new ArrayList<>();
  private final GroovyClassDescriptor myDescriptor;
  @Nullable private final Map<String, List> myBindings;

  public CustomMembersGenerator(@NotNull GroovyClassDescriptor descriptor, @Nullable Map<String, List> bindings) {
    myDescriptor = descriptor;
    myBindings = bindings;
  }

  @Override
  public PsiElement getPlace() {
    return myDescriptor.getPlace();
  }

  @Override
  @Nullable
  public PsiClass getClassType() {
    return getPsiClass();
  }

  @Override
  public PsiType getPsiType() {
    return myDescriptor.getPsiType();
  }

  @Nullable
  @Override
  public PsiClass getPsiClass() {
    return myDescriptor.getPsiClass();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myDescriptor.getResolveScope();
  }

  @Override
  public Project getProject() {
    return myDescriptor.getProject();
  }

  @Nullable
  public List<CustomMembersHolder> getMembersHolder() {
    if (!myDeclarations.isEmpty()) {
      addMemberHolder(new CustomMembersHolderImpl(myDeclarations));
    }
    return myMemberHolders;
  }

  @Override
  public void addMemberHolder(CustomMembersHolder holder) {
    myMemberHolders.add(holder);
  }

  private Object[] constructNewArgs(Object[] args) {
    final Object[] newArgs = new Object[args.length + 1];
    //noinspection ManualArrayCopy
    for (int i = 0; i < args.length; i++) {
      newArgs[i] = args[i];
    }
    newArgs[args.length] = this;
    return newArgs;
  }


  /** **********************************************************
   Methods to add new behavior
   *********************************************************** */
  public void property(Map<Object, Object> args) {
    if (args == null) return;

    String name = (String)args.get("name");
    Object type = args.get("type");
    Object doc = args.get("doc");
    Object docUrl = args.get("docUrl");
    Boolean isStatic = (Boolean)args.get("isStatic");

    Map<@NonNls Object, Object> getter = new HashMap<>();
    getter.put("name", GroovyPropertyUtils.getGetterNameNonBoolean(name));
    getter.put("type", type);
    getter.put("isStatic", isStatic);
    getter.put("doc", doc);
    getter.put("docUrl", docUrl);
    method(getter);

    Map<@NonNls Object, @NonNls Object> setter = new HashMap<>();
    setter.put("name", GroovyPropertyUtils.getSetterName(name));
    setter.put("type", "void");
    setter.put("isStatic", isStatic);
    setter.put("doc", doc);
    setter.put("docUrl", docUrl);
    final HashMap<Object, Object> param = new HashMap<>();
    param.put(name, type);
    setter.put("params", param);
    method(setter);
  }

  public void constructor(Map<Object, Object> args) {
    if (args == null) return;

    args.put("constructor", true);
    method(args);
  }

  public NamedParameterDescriptor parameter(Map<?, ?> args) {
    return parseNamedParameter(args);
  }

  public void method(Map<?, ?> args) {
    if (args == null) {
      return;
    }
    myDeclarations = myDeclarations.prepend(parseMethod(args));
  }

  public void methodCall(Closure<Map<Object, Object>> generator) {
    PsiElement place = myDescriptor.getPlace();

    PsiElement parent = place.getParent();
    if (isMethodCall(place, parent)) {
      assert parent instanceof GrMethodCall && place instanceof GrReferenceExpression;

      GrReferenceExpression ref = (GrReferenceExpression)place;

      PsiType[] argTypes = PsiUtil.getArgumentTypes(ref, false);
      if (argTypes == null) return;

      String[] types =
      ContainerUtil.map(argTypes, PsiType::getCanonicalText, new String[argTypes.length]);

      generator.setDelegate(this);

      HashMap<String, Object> args = new HashMap<>();
      args.put("name", ref.getReferenceName());
      args.put("argumentTypes", types);
      generator.call(args);
    }
  }

  private static boolean isMethodCall(PsiElement place, PsiElement parent) {
    return place instanceof GrReferenceExpression &&
        parent instanceof GrMethodCall &&
        ((GrMethodCall)parent).getInvokedExpression() == place;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void closureInMethod(Map<Object, Object> args) {
    if (args == null) {
      return;
    }
    ClosureDescriptor descriptor = parseClosure(args);
    if (descriptor == null) {
      return;
    }
    myDeclarations = myDeclarations.prepend(descriptor);
  }

  public void variable(Map<Object, Object> args) {
    if (args == null) {
      return;
    }
    myDeclarations = myDeclarations.prepend(parseVariable(args));
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public Object methodMissing(String name, Object args) {
    final Object[] newArgs = constructNewArgs((Object[])args);

    // Get other DSL methods from extensions
    for (GdslMembersProvider provider : PROVIDERS) {
      final List<MetaMethod> variants = DefaultGroovyMethods.getMetaClass(provider).respondsTo(provider, name, newArgs);
      if (variants.size() == 1) {
        return InvokerHelper.invokeMethod(provider, name, newArgs);
      }
    }
    return null;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public Object propertyMissing(String name) {
    if (myBindings != null) {
      final List list = myBindings.get(name);
      if (list != null) {
        return list;
      }
    }

    return null;
  }
}
