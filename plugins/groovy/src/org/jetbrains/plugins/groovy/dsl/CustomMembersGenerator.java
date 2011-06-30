package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;

/**
 * @author peter
 */
public class CustomMembersGenerator extends GroovyObjectSupport implements GdslMembersHolderConsumer {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.CustomMembersGenerator");
  private static final GdslMembersProvider[] PROVIDERS = GdslMembersProvider.EP_NAME.getExtensions();
  public static final String THROWS = "throws";
  private final Set<Map> myMethods = new HashSet<Map>();
  private final Project myProject;
  private final CompoundMembersHolder myDepot = new CompoundMembersHolder();
  private final GroovyClassDescriptor myDescriptor;
  @Nullable private final Map<String, List> myBindings;
  private final String myQualifiedName;

  public CustomMembersGenerator(GroovyClassDescriptor descriptor, PsiType type, Map<String, List> bindings) {
    myDescriptor = descriptor;
    myBindings = bindings;
    myProject = descriptor.getProject();
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
  public PsiClass getClassType() {
    return getPsiClass();
  }

  public PsiType getPsiType() {
    return myDescriptor.getPsiType();
  }

  @Nullable
  @Override
  public PsiClass getPsiClass() {
    if (myQualifiedName==null) return null;
    return JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, myDescriptor.getResolveScope());
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myDescriptor.getResolveScope();
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public CustomMembersHolder getMembersHolder() {
    if (!myMethods.isEmpty()) {
      addMemberHolder(new CustomMembersHolder() {
        @Override
        public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
          return NonCodeMembersHolder.generateMembers(myMethods, descriptor.justGetPlaceFile()).processMembers(descriptor, processor, state);
        }
      });
    }
    return myDepot;
  }

  public void addMemberHolder(CustomMembersHolder holder) {
    myDepot.addHolder(holder);
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
    final Object toThrow = args.get(THROWS);
    if (toThrow instanceof List) {
      final ArrayList<String> list = new ArrayList<String>();
      for (Object o : (List)toThrow) {
        list.add(stringifyType(o));
      }
      args.put(THROWS, list);
    }
    else if (toThrow != null) {
      args.put(THROWS, Collections.singletonList(stringifyType(toThrow)));
    }
    myMethods.add(args);
  }

  private static String stringifyType(Object type) {
    if (type == null) return CommonClassNames.JAVA_LANG_OBJECT;
    if (type instanceof Closure) return GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
    if (type instanceof Map) return "java.util.Map";
    if (type instanceof Class) return ((Class)type).getName();

    String s = type.toString();
    LOG.assertTrue(!s.startsWith("? extends"), s);
    return s;
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
