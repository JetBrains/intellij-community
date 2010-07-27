package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import groovy.lang.Closure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class CustomMembersGenerator implements GdslMembersHolderConsumer {
  private final Set<Map> myMethods = new HashSet<Map>();
  private final Project myProject;
  private final String myTypeText;
  private final CompoundMembersHolder myDepot = new CompoundMembersHolder();
  private final GroovyClassDescriptor myDescriptor;
  private final String myQualifiedName;

  public CustomMembersGenerator(GroovyClassDescriptor descriptor) {
    myDescriptor = descriptor;
    myProject = descriptor.getProject();
    myTypeText = descriptor.getTypeText();
    final PsiType type = descriptor.getPsiType();
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null) {
        myQualifiedName = psiClass.getQualifiedName();
      } else {
        myQualifiedName = null;
      }
    } else {
      myQualifiedName = null;
    }
  }

  public PsiElement getPlace() {
    return myDescriptor.getPlace();
  }

  @Nullable
  public PsiType getClassType() {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    try {
      return factory.createTypeFromText(myTypeText, getPlace());
    }
    catch (IncorrectOperationException e) {
      final PsiClass psiClass = getPsiClass();
      if (psiClass != null) return factory.createType(psiClass);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiClass getPsiClass() {
    if (myQualifiedName==null) return null;
    return JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, myDescriptor.getResolveScope());
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public CustomMembersHolder getMembersHolder() {
    // Add non-code members holder
    if (!myMethods.isEmpty()) {
      addMemberHolder(NonCodeMembersHolder.generateMembers(myMethods, myDescriptor.getPlaceFile()));
    }
    return myDepot;
  }

  public void addMemberHolder(CustomMembersHolder holder) {
    myDepot.addHolder(holder);
  }

  protected Object[] constructNewArgs(Object[] args) {
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
    String name = (String)args.get("name");
    Object type = args.get("type");
    Boolean isStatic = (Boolean)args.get("isStatic");

    Map<Object, Object> getter = new HashMap<Object, Object>();
    getter.put("name", GroovyPropertyUtils.getGetterNameNonBoolean(name));
    getter.put("type", type);
    getter.put("isStatic", isStatic);
    method(getter);

    Map<Object, Object> setter = new HashMap<Object, Object>();
    setter.put("name", GroovyPropertyUtils.getSetterName(name));
    setter.put("type", "void");
    setter.put("isStatic", isStatic);
    final HashMap<Object, Object> param = new HashMap<Object, Object>();
    param.put(name, type);
    setter.put("params", param);
    method(setter);
  }

  public void method(Map<Object, Object> args) {
    args.put("type", stringifyType(args.get("type")));
    //noinspection unchecked
    final Map<Object, Object> params = (Map)args.get("params");
    if (params != null) {
      for (Map.Entry<Object, Object> entry : params.entrySet()) {
        entry.setValue(stringifyType(entry.getValue()));
      }
    }
    myMethods.add(args);
  }

  private static String stringifyType(Object type) {
    return type instanceof Closure ? "groovy.lang.Closure" :
    type instanceof Map ? "java.util.Map" :
    type instanceof Class ? ((Class)type).getName() :
    type != null ? type.toString() : "java.lang.Object";
  }

}
